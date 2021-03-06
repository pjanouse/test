package org.jboss.qa.hornetq.test.compatibility;


import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsTransAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Session;


/**
 * Test base class for client compatibility against EAP6.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
/**
 * @tpChapter Backward compatibility testing
 * @tpSubChapter COMPATIBILITY OF JMS CLIENTS - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-integration-client-compatability-EAP-6x-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 * @tpTestCaseDetails Test base class for older client compatibility against EAP7.
 * ClientCompatibilityTestBase
 */
@RunWith(Arquillian.class)
public abstract class ClientCompatibilityTestBase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(ClientCompatibilityTestBase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;

    protected static final int NUMBER_OF_DESTINATIONS = 1;

    protected static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 1;

    protected static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 1;

    protected static final String QUEUE_NAME_PREFIX = "testQueue";

    protected static final String TOPIC_NAME_PREFIX = "testTopic";

    protected static final String QUEUE_JNDI_NAME_PREFIX = "jms/queue/testQueue";

    protected static final String TOPIC_JNDI_NAME_PREFIX = "jms/topic/testTopic";

    protected static final String JOURNAL_DIR = JOURNAL_DIRECTORY_A;

    /**
     * @tpTestDetails This test scenario tests whether is possible to send and receive messages from queue with older EAP clients
     * on latest EAP7 server.
     * @tpProcedure <ul>
     *     <li>start EAP7 node</li>
     *     <li>start older clients (with AUTO_ACKNOWLEDGE) sessions sending and receiving messages from testQueue on EAP7 server.
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and receiver successfully sent and received messages
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testAutoAckQueue() throws Exception {
        testClient(container(1), Session.AUTO_ACKNOWLEDGE, false);
    }


    /**
     * @tpTestDetails This test scenario tests whether is possible to send and receive messages from queue with older EAP clients
     * on latest EAP7 server.
     * @tpProcedure <ul>
     *     <li>start EAP7 node</li>
     *     <li>start older clients (with CLIENT_ACKNOWLEDGE) sessions sending and receiving messages from testQueue on EAP7 server.
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and receiver successfully sent and received messages
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testAckQueue() throws Exception {
        testClient(container(1), Session.CLIENT_ACKNOWLEDGE, false);
    }

    /**
     * @tpTestDetails This test scenario tests whether is possible to send and receive messages from queue with older EAP clients
     * on latest EAP7 server.
     * @tpProcedure <ul>
     *     <li>start EAP7 node</li>
     *     <li>start older clients (with SESSION_TRANSACTED) sessions sending and receiving messages from testQueue on EAP7 server.
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and receiver successfully sent and received messages
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTransAckQueue() throws Exception {
        testClient(container(1), Session.SESSION_TRANSACTED, false);
    }


    /**
     * @tpTestDetails This test scenario tests whether is possible to send and receive messages from topic with older EAP clients
     * on latest EAP7 server.
     * @tpProcedure <ul>
     *     <li>start EAP7 node</li>
     *     <li>start older clients (with AUTO_ACKNOWLEDGE) sessions sending and receiving messages from testTopic on EAP7 server.
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and receiver successfully sent and received messages
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testAutoAckTopic() throws Exception {
        testClient(container(1), Session.AUTO_ACKNOWLEDGE, true);
    }


    /**
     * @tpTestDetails This test scenario tests whether is possible to send and receive messages from topic with older EAP clients
     * on latest EAP7 server.
     * @tpProcedure <ul>
     *     <li>start EAP7 node</li>
     *     <li>start older clients (with CLIENT_ACKNOWLEDGE) sessions sending and receiving messages from testTopic on EAP7 server.
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and receiver successfully sent and received messages
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testClientAckTopic() throws Exception {
        testClient(container(1), Session.CLIENT_ACKNOWLEDGE, true);
    }

    /**
     * @tpTestDetails This test scenario tests whether is possible to send and receive messages from topic with older EAP clients
     * on latest EAP7 server.
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
    public void testTransAckTopic() throws Exception {
        testClient(container(1), Session.SESSION_TRANSACTED, true);
    }


    protected void testClient(final Container container, final int acknowledgeMode, final boolean isTopic)
            throws Exception {

        this.prepareContainer(container);
        container.start();

        Clients client = createClients(container, acknowledgeMode, isTopic);
        client.startClients();

        ClientUtils.waitForClientsToFinish(client, 600000);

        container.stop();

        Assert.assertTrue("There are failures detected by clients. More information in log.", client.evaluateResults());
    }


    abstract protected void prepareContainer(final Container container) throws Exception;


    protected Clients createClients(final Container container, final int acknowledgeMode, final boolean isTopic)
            throws Exception {

        Clients clients;

        if (isTopic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(container, TOPIC_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(container, TOPIC_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(container, TOPIC_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
                clients.setProducedMessagesCommitAfter(10);

            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(container, QUEUE_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(container, QUEUE_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(container, QUEUE_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
                clients.setProducedMessagesCommitAfter(10);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }

        clients.setMessageBuilder(new ClientMixMessageBuilder(10, 200));

        for (Client c : clients.getProducers()) {
            c.setTimeout(0);
        }

        for (Client c : clients.getConsumers()) {
            c.setTimeout(0);
        }

        usedClients.addAll(clients.getConsumers());
        usedClients.addAll(clients.getProducers());

        return clients;
    }

}
