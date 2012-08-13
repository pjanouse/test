package org.jboss.qa.hornetq.test.perf;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.perf.CounterMdb;
import org.jboss.qa.hornetq.apps.perf.PerformanceConstants;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.hornetq.test.JMSTools;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;

/**
 * Basic performance tests measured inside application server.
 * MDB consumes messages from <code>inQueue</code> and sends them back.
 * After defined count of cycles calculates time and sends messages into the <code>outQueue</code>.
 * Test client consumes all messages from <code>outQueue</code> and calculates statistics.
 * <p/>
 *
 * @author pslavice@redhat.com
 */
@RunWith(Arquillian.class)
public class SimpleContainerPerformanceTest extends HornetQTestCase {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    private static final String MDB_DEPLOY = "mdbPerformanceMDB";

    /**
     * Stops all servers
     */
    @Before
    @After
    public void stopAllServers() {
        stopServer(CONTAINER1);
    }

    /**
     * Prepares archive with the test MDB
     *
     * @return archive
     * @throws Exception if something is wrong
     */
    @Deployment(managed = false, testable = false, name = MDB_DEPLOY)
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createArchiveWithPerformanceMdb() throws Exception {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "performanceMdb.jar");
        mdbJar.addClass(CounterMdb.class);
        mdbJar.addClass(PerformanceConstants.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming,org.hornetq\n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * Normal message (not large message), byte message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void normalByteMessagesTest() throws InterruptedException {
        deleteDataFolderForJBoss1();
        testLogic(1000, 10000, new ByteMessageBuilder(512));
    }

    /**
     * Normal message (not large message), text message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void normalTextMessagesTest() throws InterruptedException {
        deleteDataFolderForJBoss1();
        testLogic(1000, 10000, new TextMessageBuilder(512));
    }

    /**
     * Large message, byte message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void largeByteMessagesTest() throws InterruptedException {
        deleteDataFolderForJBoss1();
        testLogic(1000, 10000, new ByteMessageBuilder(150 * 1024));
    }

    /**
     * Large message, text message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void largeTextMessagesTest() throws InterruptedException {
        deleteDataFolderForJBoss1();
        testLogic(1000, 10000, new TextMessageBuilder(150 * 1024));
    }

    /**
     * Implementation of the test logic, logic is shared for all test scenarios
     *
     * @param messagesCount  total count of messages
     * @param cyclesCount    defines how many times will be message returned into the inQueue
     * @param messageBuilder implementation of {@link org.jboss.qa.hornetq.apps.MessageBuilder} used for test
     */
    private void testLogic(int messagesCount, int cyclesCount, MessageBuilder messageBuilder) {
        final String IN_QUEUE = "InQueue";
        final String OUT_QUEUE = "OutQueue";

        log.info("Staring container for test ....");
        controller.start(CONTAINER1);
        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);
        jmsAdminOperations.createQueue(IN_QUEUE, IN_QUEUE);
        jmsAdminOperations.createQueue(OUT_QUEUE, OUT_QUEUE);
        log.info("Deploying mdb for test ....");

        deployer.deploy(MDB_DEPLOY);

        int MAX_WAIT_TIME = 10 * 60; // 10 minutes by default
        String tmpMaxWaitTime = System.getenv(PerformanceConstants.MAX_WAIT_TIME_PARAM);
        if (tmpMaxWaitTime != null) {
            try {
                MAX_WAIT_TIME = Integer.parseInt(tmpMaxWaitTime);
            } catch (NumberFormatException e) {
                log.error(e.getMessage(), e);
                MAX_WAIT_TIME = 30 * 60;
            }
        }

        // Sends all messages into the server
        Context context = null;
        Connection connection = null;
        Session session = null;
        long startTime = System.currentTimeMillis();
        try {
            context = this.getContext();
            ConnectionFactory cf = (ConnectionFactory) context.lookup(this.getConnectionFactoryName());
            connection = cf.createConnection();
            connection.start();
            Queue inQueue = (Queue) context.lookup(IN_QUEUE);
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(inQueue);
            log.info(String.format("We will send %s messages to server", messagesCount));
            for (int i = 0; i < messagesCount; i++) {
                Message message = messageBuilder.createMessage(session);
                message.setIntProperty(PerformanceConstants.MESSAGE_PARAM_INDEX, i);
                message.setIntProperty(PerformanceConstants.MESSAGE_PARAM_CYCLES, cyclesCount);
                producer.send(message);
                if (i > 0 && i % 1000 == 0) {
                    log.info(String.format("Sent %s messages to server", i));
                }
            }
            producer.close();

            log.info(String.format("We will receive %s messages to server", messagesCount));
            log.info(String.format("We will wait max %s s", MAX_WAIT_TIME));
            long waitForMessagesStart = System.currentTimeMillis();
            long msgsInInQueue;
            while ((msgsInInQueue = jmsAdminOperations.getCountOfMessagesOnQueue(IN_QUEUE)) > 0L) {
                log.info(String.format(" ... Waiting cycle, in queue has %s messages", msgsInInQueue));
                Thread.sleep(10000);
                if ((System.currentTimeMillis() - waitForMessagesStart) / 1000 > MAX_WAIT_TIME) {
                    fail("Cannot wait more time for messages");
                }
            }

            // Receive messages from out queue
            Map<Integer, Long> results = new HashMap<Integer, Long>(messagesCount);
            Queue outQueue = (Queue) context.lookup(OUT_QUEUE);
            MessageConsumer consumer = session.createConsumer(outQueue);
            Message msg;
            while ((msg = consumer.receive(60000)) != null) {
                try {
                    int index = msg.getIntProperty(PerformanceConstants.MESSAGE_PARAM_INDEX);
                    long start = msg.getLongProperty(PerformanceConstants.MESSAGE_PARAM_CREATED);
                    long end = msg.getLongProperty(PerformanceConstants.MESSAGE_PARAM_FINISHED);
                    long totalMs = (end - start) / 1000000;
                    results.put(index, totalMs);
                } catch (NumberFormatException e) {
                    log.error(e.getMessage(), e);
                }
            }

            // Calculate stats
            long minValue = Long.MAX_VALUE;
            long maxValue = Long.MIN_VALUE;
            long sum = 0;
            for (Integer index : results.keySet()) {
                Long result = results.get(index);
                minValue = (minValue < result) ? result : minValue;
                maxValue = (maxValue > result) ? result : maxValue;
                sum += result;
            }
            log.info("########################################################");
            log.info(String.format(" Min time : %s ms", minValue));
            log.info(String.format(" Max time : %s ms", maxValue));
            log.info(String.format(" Avg time : %s ms", sum / results.size()));
            log.info(String.format(" Messages : %s ms", results.size()));
            log.info("########################################################");
            if (results.size() != messagesCount) {
                fail("Unexpected count of messages!");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            fail(e.getMessage());
        } finally {
            JMSTools.cleanupResources(context, connection, session);
        }
        log.info(String.format("Ending test after %s ms", System.currentTimeMillis() - startTime));
        jmsAdminOperations.removeQueue(IN_QUEUE);
        jmsAdminOperations.removeQueue(OUT_QUEUE);
        jmsAdminOperations.close();
        log.info("Stopping container for test ....");
        stopServer(CONTAINER1);
    }
}
