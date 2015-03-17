package org.jboss.qa.hornetq.test.compatibility;


import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Session;


/**
 * Test base class for client compatibility against EAP6.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
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


    @Before
    public void startContainerBeforeTest() {
        this.controller.start(CONTAINER1);
    }


    @After
    public void stopContainerAfterTest() {
        this.controller.stop(CONTAINER1);
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testAutoAckQueue() throws Exception {
        testClient(CONTAINER1_INFO, Session.AUTO_ACKNOWLEDGE, false);
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testAckQueue() throws Exception {
        testClient(CONTAINER1_INFO, Session.CLIENT_ACKNOWLEDGE, false);
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTransAckQueue() throws Exception {
        testClient(CONTAINER1_INFO, Session.SESSION_TRANSACTED, false);
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testAutoAckTopic() throws Exception {
        testClient(CONTAINER1_INFO, Session.AUTO_ACKNOWLEDGE, true);
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testClientAckTopic() throws Exception {
        testClient(CONTAINER1_INFO, Session.CLIENT_ACKNOWLEDGE, true);
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTransAckTopic() throws Exception {
        testClient(CONTAINER1_INFO, Session.SESSION_TRANSACTED, true);
    }


    private void testClient(final ContainerInfo container, final int acknowledgeMode, final boolean isTopic)
            throws Exception {

        this.prepareContainer(container);
        controller.start(container.getName());

        Clients client = createClients(container, acknowledgeMode, isTopic);
        client.startClients();

        long startTime = System.currentTimeMillis();

        while (!client.isFinished() && System.currentTimeMillis() - startTime < 300000) {
            LOG.info("Waiting for client " + client + " to finish.");
            Thread.sleep(1500);
        }

        stopServer(container.getName());

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", client.evaluateResults());
    }


    abstract protected void prepareContainer(final ContainerInfo container) throws Exception;


    private Clients createClients(final ContainerInfo container, final int acknowledgeMode, final boolean isTopic)
            throws Exception {

        Clients clients;

        if (isTopic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(container.getContainerType().name(), container.getIpAddress(),
                        this.getLegacyClientJndiPort(), TOPIC_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(container.getContainerType().name(), container.getIpAddress(),
                        this.getLegacyClientJndiPort(), TOPIC_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(container.getContainerType().name(), container.getIpAddress(),
                        this.getLegacyClientJndiPort(), TOPIC_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
                clients.setProducedMessagesCommitAfter(10);

            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(container.getContainerType().name(), container.getIpAddress(),
                        this.getLegacyClientJndiPort(), QUEUE_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(container.getContainerType().name(), container.getIpAddress(),
                        this.getLegacyClientJndiPort(), QUEUE_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(container.getContainerType().name(), container.getIpAddress(),
                        this.getLegacyClientJndiPort(), QUEUE_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
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

        return clients;
    }


    abstract protected int getLegacyClientJndiPort();

}
