//TODO do check of journal files
package org.jboss.qa.hornetq.test.failover;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MixMessageBuilder;
import org.jboss.qa.hornetq.test.prepares.specific.Lodh4Prepare;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Lodh4 - cluster A -> bridge (core) -> cluster B. Kill server from A or B repeatedly
 * Topology - 1,3 - source containers, 2,4 - target containers
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter HORNETQ CORE BRIDGES - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh4/  
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Prepare("Lodh4Prepare")
public class Lodh4TestCase extends HornetQTestCase {

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;
    // Logger
    private static final Logger log = Logger.getLogger(Lodh4TestCase.class);


    /**
     * Normal message (not large message), byte message
     *
     * @throws InterruptedException if something is wrong
     */

    /**
     * @tpTestDetails Test whether the kill of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends byte messages to InQueue to container 1 and consumer which
     *                  reads messages from OutQueue from container 4</li>
     *              <li>kill and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void normalMessagesKillTest() throws Exception {
        testLogic(new ByteMessageBuilder(30));
    }

    /**
     * @tpTestDetails Test whether the shutdown of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends byte messages to InQueue to container 1 and consumer which
     *                  reads messages from OutQueue from container 4</li>
     *              <li>shutdown and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void normalMessagesShutdownTest() throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogic(new ByteMessageBuilder(30), killSequence, true);
    }

    /**
     * @tpTestDetails Test whether the kill of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends large byte messages to InQueue to container 1 and consumer which
     *                  reads messages from OutQueue from container 4</li>
     *              <li>kill and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void largeByteMessagesKillTest() throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogicLargeMessages(new ByteMessageBuilder(300 * 1024), killSequence, false);
    }

    /**
     * @tpTestDetails Test whether the shutdown of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends large byte messages to InQueue to container 1 and consumer which
     *                  reads messages from OutQueue from container 4</li>
     *              <li>shutdown and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void largeByteMessagesShutdownTest() throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogicLargeMessages(new ByteMessageBuilder(300 * 1024), killSequence, true);
    }

    /**
     * @tpTestDetails Test whether the kill of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends large messages of various types to InQueue to container 1 and
     *                  consumer which reads messages from OutQueue from container 4</li>
     *              <li>kill and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void mixMessagesKillTest() throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogicLargeMessages(new MixMessageBuilder(300 * 1024), killSequence, false);
    }

    /**
     * @tpTestDetails Test whether the shutdown of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends large messages of various types to InQueue to container 1 and
     *                  consumer which reads messages from OutQueue from container 4</li>
     *              <li>shutdown and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void mixMessagesShutdownTest() throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogicLargeMessages(new MixMessageBuilder(300 * 1024), killSequence, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(params = {
            @Param(name = Lodh4Prepare.VARIANT, value = "THREE_NODES")
    })
    @Ignore("Unignore once jira - https://issues.jboss.org/browse/JBEAP-2214 - is implemented")
    public void testClusterWithCoreBridge() throws Exception {

        container(1).start();
        container(3).start();
        container(4).start();

        // give some time to server4 to really start
        Thread.sleep(3000);

        QueueClientsClientAck clientsA1 = new QueueClientsClientAck(
                container(1),
                Lodh4Prepare.IN_QUEUE_JNDI_PREFIX,
                Lodh4Prepare.NUMBER_OF_DESTINATIONS,
                1,
                1,
                NUMBER_OF_MESSAGES_PER_PRODUCER);

        clientsA1.setQueueJndiNamePrefixProducers(Lodh4Prepare.IN_QUEUE_JNDI_PREFIX);
        clientsA1.setQueueJndiNamePrefixConsumers(Lodh4Prepare.OUT_QUEUE_JNDI_PREFIX);
        clientsA1.setContainerForConsumers(container(4));
        clientsA1.setMessageBuilder(new ByteMessageBuilder(30));
        clientsA1.startClients();

        // Get producers some time to send messages
        Thread.sleep(10000);

        clientsA1.stopClients();

        while (!clientsA1.isFinished()) {
            Thread.sleep(1000);
        }

        container(1).stop();
        container(3).stop();
        container(4).stop();

        assertTrue("There are problems detected by org.jboss.qa.hornetq.apps.clients. Check logs for more info. Look for: 'Print kill sequence', "
                + "'Kill and restart server', 'Killing server', 'Evaluate results for queue org.jboss.qa.hornetq.apps.clients with client acknowledge'.", clientsA1.evaluateResults());

    }

    /**
     * @tpTestDetails We have four servers 1, 2, 3 and 4. Servers 1 and 3 are in cluster A. Servers 2 and 4 are in cluster B.
     * There are also two core bridges from 1 to 2 and from 3 to 4. Producers send messages to server 1. Consumers receive
     * messages on server 4. After some time we shutdown server 2 and we expect that all messages will be delivered to server 4
     * through server 3.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends messages to InQueue to container 1 and
     *                  consumer which reads messages from OutQueue from container 4</li>
     *              <li>shutdown server 2</li>
     *              <li>start server 2</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     * @throws Exception
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailOfOneServer() throws Exception {

        container(2).start();
        container(4).start();
        container(1).start();
        container(3).start();

        // give some time to server4 to really start
        Thread.sleep(3000);

        QueueClientsClientAck clientsA1 = new QueueClientsClientAck(
                container(1),
                Lodh4Prepare.IN_QUEUE_JNDI_PREFIX,
                Lodh4Prepare.NUMBER_OF_DESTINATIONS,
                1,
                1,
                NUMBER_OF_MESSAGES_PER_PRODUCER);

        clientsA1.setQueueJndiNamePrefixProducers(Lodh4Prepare.IN_QUEUE_JNDI_PREFIX);
        clientsA1.setQueueJndiNamePrefixConsumers(Lodh4Prepare.OUT_QUEUE_JNDI_PREFIX);
        clientsA1.setContainerForConsumers(container(4));
        clientsA1.setMessageBuilder(new ByteMessageBuilder(30));
        clientsA1.startClients();

        // Get producers some time to send messages
        Thread.sleep(10000);

        container(2).stop();
        Thread.sleep(10000);
        container(2).start();

        clientsA1.stopClients();

        while (!clientsA1.isFinished()) {
            Thread.sleep(1000);
        }

        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

        assertTrue("There are problems detected by org.jboss.qa.hornetq.apps.clients. Check logs for more info. Look for: 'Print kill sequence', "
                + "'Kill and restart server', 'Killing server', 'Evaluate results for queue org.jboss.qa.hornetq.apps.clients with client acknowledge'.", clientsA1.evaluateResults());

    }

    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     *
     * @param messageBuilder instance of the message builder
     */
    private void testLogic(MessageBuilder messageBuilder) throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogic(messageBuilder, killSequence, false);
    }

    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     *
     * @param messageBuilder instance of the message builder
     */
    private void testLogic(MessageBuilder messageBuilder, List<Container> killSequence, boolean shutdown) throws Exception {

        container(2).start();
        container(4).start();
        container(1).start();
        container(3).start();

        // give some time to server4 to really start
        Thread.sleep(3000);

        QueueClientsClientAck clientsA1 = new QueueClientsClientAck(
                container(1),
                Lodh4Prepare.IN_QUEUE_JNDI_PREFIX,
                Lodh4Prepare.NUMBER_OF_DESTINATIONS,
                1,
                1,
                NUMBER_OF_MESSAGES_PER_PRODUCER);

        clientsA1.setQueueJndiNamePrefixProducers(Lodh4Prepare.IN_QUEUE_JNDI_PREFIX);
        clientsA1.setQueueJndiNamePrefixConsumers(Lodh4Prepare.OUT_QUEUE_JNDI_PREFIX);
        clientsA1.setContainerForConsumers(container(4));
        clientsA1.setMessageBuilder(messageBuilder);
        clientsA1.startClients();

        executeNodeFailSequence(killSequence, 10000, shutdown);

        clientsA1.stopClients();

        while (!clientsA1.isFinished()) {
            Thread.sleep(1000);
        }
        
        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

        assertTrue("There are problems detected by org.jboss.qa.hornetq.apps.clients. Check logs for more info. Look for: 'Print kill sequence', "
                + "'Kill and restart server', 'Killing server', 'Evaluate results for queue org.jboss.qa.hornetq.apps.clients with client acknowledge'.", clientsA1.evaluateResults());

    }

    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     *
     * @param messageBuilder instance of the message builder
     * @param killSequence   kill sequence for servers
     * @param shutdown       clean shutdown?
     */
    private void testLogicLargeMessages(MessageBuilder messageBuilder, List<Container> killSequence, boolean shutdown) throws Exception {

        container(2).start();
        container(4).start();
        container(1).start();
        container(3).start();

        // give some time to server4 to really start
        Thread.sleep(3000);

        ProducerTransAck producer1 = new ProducerTransAck(container(1), Lodh4Prepare.IN_QUEUE_JNDI_PREFIX + 0, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageBuilder(messageBuilder);
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(4), Lodh4Prepare.OUT_QUEUE_JNDI_PREFIX + 0, 100000, 10, 10);

        log.info("Start producer and receiver.");
        producer1.start();
        receiver1.start();

        executeNodeFailSequence(killSequence, 20000, shutdown);

        producer1.stopSending();
        producer1.join();
        receiver1.join();


        log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

    }

    /**
     * Executes kill sequence.
     *
     * @param failSequence     map Contanier -> ContainerIP
     * @param timeBetweenFails time between subsequent kills (in milliseconds)
     */
    private void executeNodeFailSequence(List<Container> failSequence, long timeBetweenFails, boolean shutdown) throws InterruptedException {

        if (shutdown) {
            for (Container container : failSequence) {
                Thread.sleep(timeBetweenFails);
                log.info("Shutdown server: " + container.getName());
                container.stop();
                Thread.sleep(3000);
                log.info("Start server: " + container.getName());
                container.start();
                log.info("Server: " + container.getName() + " -- STARTED");
            }
        } else {
            for (Container container : failSequence) {
                Thread.sleep(timeBetweenFails);
                container.kill();
                Thread.sleep(3000);
                log.info("Start server: " + container.getName());
                container.start();
                log.info("Server: " + container.getName() + " -- STARTED");
            }
        }
    }


}
