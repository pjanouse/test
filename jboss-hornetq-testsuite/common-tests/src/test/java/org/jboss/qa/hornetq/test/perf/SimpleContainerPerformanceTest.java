package org.jboss.qa.hornetq.test.perf;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.perf.CounterMdb;
import org.jboss.qa.hornetq.apps.perf.PerformanceConstants;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.naming.Context;

import static org.junit.Assert.fail;

/**
 * Basic performance test which is executed inside container.
 * <p/>
 * MDB consumes messages from <code>inQueue</code> and sends them back. After defined count of cycles calculates time and sends
 * messages into the <code>outQueue</code>. Test client consumes all messages from <code>outQueue</code> and calculates
 * statistics.
 * <p/>
 * Configuration parameters:
 * <ul>
 * <li>performance.wait - defines maximal wait time which will test wait for the messages in output queue (sec)</li>
 * <li>performance.messages - count of the messages used in test</li>
 * <li>performance.messages_cycles - count of the used cycles inside the container for the each message</li>
 * <li>performance.large_messages - count of the large messages used in test</li>
 * <li>performance.large_messages_cycles - count of the used cycles inside the container for the each large message</li>
 * <li>performance.large_messages_length - length of the large messages (kb)</li>
 * </ul>
 * 
 * @tpChapter PERFORMANTCE TESTING
 * @tpSubChapter HORNETQ LOAD TEST
 * @tpJobLink tbd
 * @tpTcmsLink tbd
 * @tpTestCaseDetails Basic performance tests which are executed inside container.
 * MDB consumes messages from inQueue and sends them back. After defined count
 * of cycles calculates time and sends messages into the outQueue. Test client
 * consumes all messages from outQueue and calculates statistics.
 * 
 * @author pslavice@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class SimpleContainerPerformanceTest extends HornetQTestCase {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    // ID of the deployment
    private final JavaArchive MDB_DEPLOY = createArchiveWithPerformanceMdb();

    private static int MESSAGES = 100;

    private static int LARGE_MESSAGES = 100;

    private static int MESSAGE_CYCLES = 10;

    private static int LARGE_MESSAGES_CYCLES = 10;

    private static int LARGE_MESSAGES_LENGTH = 250;

    private static int MAX_WAIT_TIME = 10 * 60; // 10 minutes by default

    static {
        MAX_WAIT_TIME = parseIntFromSysProp(PerformanceConstants.MAX_WAIT_TIME_PARAM, MAX_WAIT_TIME);
        MESSAGES = parseIntFromSysProp(PerformanceConstants.MESSAGES_COUNT_PARAM, MESSAGES);
        MESSAGE_CYCLES = parseIntFromSysProp(PerformanceConstants.MESSAGES_CYCLES_PARAM, MESSAGE_CYCLES);
        LARGE_MESSAGES = parseIntFromSysProp(PerformanceConstants.LARGE_MESSAGES_COUNT_PARAM, LARGE_MESSAGES);
        LARGE_MESSAGES_CYCLES = parseIntFromSysProp(PerformanceConstants.LARGE_MESSAGES_CYCLES_PARAM, LARGE_MESSAGES_CYCLES);
        LARGE_MESSAGES_LENGTH = parseIntFromSysProp(PerformanceConstants.LARGE_MESSAGES_LENGTH, LARGE_MESSAGES_LENGTH);

        log.info(String.format("Setting %s s as max wait time for receive", MAX_WAIT_TIME));
        log.info(String.format("Setting %s messages for test", MESSAGES));
        log.info(String.format("Setting %s cycles for messages", MESSAGE_CYCLES));
        log.info(String.format("Setting %s large messages for test", LARGE_MESSAGES));
        log.info(String.format("Setting %s cycles for large messages messages", LARGE_MESSAGES_CYCLES));
        log.info(String.format("Setting %s kb length for large messages messages", LARGE_MESSAGES_LENGTH));
    }

    /**
     * Parses value from the system property
     * 
     * @param sysPropName name of the system property
     * @param defaultValue default value
     * @return new value or default value for the given configuration parameter
     */
    private static int parseIntFromSysProp(String sysPropName, int defaultValue) {
        int value = defaultValue;
        String tmpMessagesCount = System.getProperty(sysPropName);
        if (tmpMessagesCount != null) {
            try {
                value = Integer.parseInt(tmpMessagesCount);
            } catch (NumberFormatException e) {
                log.error(e.getMessage(), e);
            }
        }
        return value;
    }

    /**
     * Stops all servers
     */
    @Before
    @After
    public void stopAllServers() {
        container(1).stop();
    }

    /**
     * Prepares archive with the test MDB
     * 
     * @return archive
     * @throws Exception if something is wrong
     */
    public static JavaArchive createArchiveWithPerformanceMdb() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "performanceMdb.jar");
        mdbJar.addClass(CounterMdb.class);
        mdbJar.addClass(PerformanceConstants.class);
        return mdbJar;
    }

    /**
     * Normal message (not large message), byte message
     *
     * @throws InterruptedException if something is wrong
     *
     * @tpTestDetails Single server with deployed InQueue and OutQueue is
     * started. 100 normal byte messages are send into InQueue and MDB is
     * deployed. MDB sends all messages back to InQueue 10 times, then sends
     * them to OutQueue. Client receives messages from OutQueue. Statistics are
     * calculated.
     *
     * @tpProcedure <ul>
     * <li>Start server with InQueue and OutQueue deployed</li>
     * <li>Send 100 normal byte messages into InQueue</li>
     * <li>Deploy MDB</li>
     * <li>MDB sends messages back to InQueue several times, then sends them to OutQueue</li>
     * <li>Receive all messages from OutQueue</li>
     * <li>Calculate statistics</li>
     * </ul>
     * @tpPassCrit All messages are delivered to OutQueue in given time range and received by the client.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void normalByteMessagesTest() throws InterruptedException {
        testLogic(MESSAGES, MESSAGE_CYCLES, new ByteMessageBuilder(512));
    }

    /**
     * Normal message (not large message), text message
     * 
     * @throws InterruptedException if something is wrong
    *
     * @tpTestDetails Single server with deployed InQueue and OutQueue is
     * started. 100 normal text messages are send into InQueue and MDB is
     * deployed. MDB sends all messages back to InQueue 10 times, then sends
     * them to OutQueue. Client receives messages from OutQueue. Statistics are
     * calculated.
     *
     * @tpProcedure <ul>
     * <li>Start server with InQueue and OutQueue deployed</li>
     * <li>Send 100 normal text messages into InQueue</li>
     * <li>Deploy MDB</li>
     * <li>MDB sends messages back to InQueue several times, then sends them to OutQueue</li>
     * <li>Receive all messages from OutQueue</li>
     * <li>Calculate statistics</li>
     * </ul>
     * @tpPassCrit All messages are delivered to OutQueue in given time range and received by the client.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void normalTextMessagesTest() throws InterruptedException {
        testLogic(MESSAGES, MESSAGE_CYCLES, new TextMessageBuilder(512));
    }

    /**
     * Large message, byte message
     * 
     * @throws InterruptedException if something is wrong
     *
     * @tpTestDetails Single server with deployed InQueue and OutQueue is
     * started. 100 large byte messages are send into InQueue and MDB is
     * deployed. MDB sends all messages back to InQueue 10 times, then sends
     * them to OutQueue. Client receives messages from OutQueue. Statistics are
     * calculated.
     *
     * @tpProcedure <ul>
     * <li>Start server with InQueue and OutQueue deployed</li>
     * <li>Send 100 large byte messages into InQueue</li>
     * <li>Deploy MDB</li>
     * <li>MDB sends messages back to InQueue several times, then sends them to OutQueue</li>
     * <li>Receive all messages from OutQueue</li>
     * <li>Calculate statistics</li>
     * </ul>
     * @tpPassCrit All messages are delivered to OutQueue in given time range and received by the client.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void largeByteMessagesTest() throws InterruptedException {
        testLogic(LARGE_MESSAGES, LARGE_MESSAGES_CYCLES, new ByteMessageBuilder(LARGE_MESSAGES_LENGTH * 1024));
    }

    /**
     * Large message, text message
     * 
     * @throws InterruptedException if something is wrong
     *
     * @tpTestDetails Single server with deployed InQueue and OutQueue is
     * started. 100 large text messages are send into InQueue and MDB is
     * deployed. MDB sends all messages back to InQueue 10 times, then sends
     * them to OutQueue. Client receives messages from OutQueue. Statistics are
     * calculated.
     *
     * @tpProcedure <ul>
     * <li>Start server with InQueue and OutQueue deployed</li>
     * <li>Send 100 large text messages into InQueue</li>
     * <li>Deploy MDB</li>
     * <li>MDB sends messages back to InQueue several times, then sends them to OutQueue</li>
     * <li>Receive all messages from OutQueue</li>
     * <li>Calculate statistics</li>
     * </ul>
     * @tpPassCrit All messages are delivered to OutQueue in given time range and received by the client.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void largeTextMessagesTest() throws InterruptedException {
        testLogic(LARGE_MESSAGES, LARGE_MESSAGES_CYCLES, new TextMessageBuilder(LARGE_MESSAGES_LENGTH * 1024));
    }

    /**
     * Implementation of the test logic, logic is shared for all test scenarios
     * 
     * @param messagesCount total count of messages
     * @param cyclesCount defines how many times will be message returned into the inQueue
     * @param messageBuilder implementation of {@link org.jboss.qa.hornetq.apps.MessageBuilder} used for test
     */
    private void testLogic(int messagesCount, int cyclesCount, MessageBuilder messageBuilder) {
        final String IN_QUEUE_NAME = "InQueue";
        final String IN_QUEUE_JNDI_NAME = "jms/queue/"+IN_QUEUE_NAME;
        final String OUT_QUEUE_NAME = "OutQueue";
        final String OUT_QUEUE_JNDI_NAME = "jms/queue/"+OUT_QUEUE_NAME;
        container(1).start();
        log.info("Staring container for test ....");
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.createQueue(IN_QUEUE_NAME, IN_QUEUE_JNDI_NAME);
        jmsAdminOperations.createQueue(OUT_QUEUE_NAME, OUT_QUEUE_JNDI_NAME);
        jmsAdminOperations.close();
        container(1).stop();
        container(1).start();

        Context context = null;
        Connection connection = null;
        Session session = null;
        long startTime = System.currentTimeMillis();
        try {
            context = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
            connection = cf.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue inQueue = (Queue) context.lookup(IN_QUEUE_JNDI_NAME);
            Queue outQueue = (Queue) context.lookup(OUT_QUEUE_JNDI_NAME);

            // cleaning
            MessageConsumer consumer = session.createConsumer(outQueue);
            Message msg;
            int size = 0;
            while ((msg = consumer.receive(100)) != null)
                size++;
            if (size > 0)
                log.warn(String.format("Cleaned %s messages from output queue before test start!!!", size));

            consumer.close();

            // Sends all messages into the server
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

            log.info("  Deploying mdb for test ....");
            container(1).deploy(MDB_DEPLOY);
            log.info("  Receiving ....");
            long waitForMessagesStart = System.currentTimeMillis();
            boolean wait = true;
            QueueBrowser browser = session.createBrowser(outQueue);

            while (wait) {
                Thread.sleep(1000);
                if (log.isDebugEnabled()) {
                    log.debug(" No messages in output queue");
                }
                wait = !(browser.getEnumeration().hasMoreElements());

                if ((System.currentTimeMillis() - waitForMessagesStart) / 1000 > MAX_WAIT_TIME) {
                    fail("Receive timeout, output queue has still no messages");
                }
            }

            // Receive messages from out queue
            log.info(String.format("We should receive %s messages from server", messagesCount));
            consumer = session.createConsumer(outQueue);
            String messageType = null;
            long messageLength = 0;
            long start = Long.MAX_VALUE;
            long end = Long.MIN_VALUE;
            size = 0;
            while ((msg = consumer.receive(10000)) != null) {
                try {
                    messageType = msg.getStringProperty(PerformanceConstants.MESSAGE_TYPE);
                    messageLength = msg.getLongProperty(PerformanceConstants.MESSAGE_LENGTH);
                    long started = msg.getLongProperty(PerformanceConstants.MESSAGE_PARAM_CREATED);
                    long ended = msg.getLongProperty(PerformanceConstants.MESSAGE_PARAM_FINISHED);
                    if (start > started)
                        start = started;
                    if (end < ended)
                        end = ended;
                    size++;
                } catch (NumberFormatException e) {
                    log.error(e.getMessage(), e);
                }
            }
            consumer.close();
            if (size != messagesCount) {
                log.error(String.format("Client has received %s messages but should receive %s, failing tests", size,
                        messagesCount));
                fail("Unexpected count of messages!");
            }
            long sum = (end - start) / 1000000;
            int cnt = messagesCount * cyclesCount;
            log.info("########################################################");
            log.info(" Type of the last message " + messageType);
            log.info(String.format(" Length of the last message : %s bytes", messageLength));
            log.info(String.format(" Total number of messages : %s", cnt));
            log.info(String.format(" Total duration of delivery : %s ms", sum));
            log.info(String.format(" Throughput (msg/sec) : %s ", cnt * 1000 / sum));
            log.info(String.format(" Avg msg delivery time : %s ms", sum / cnt));
            log.info("########################################################");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            fail(e.getMessage());
        } finally {
            JMSTools.cleanupResources(context, connection, session);
        }
        log.info(String.format("Ending test after %s ms", System.currentTimeMillis() - startTime));
        log.info("Stopping container for test ....");
        container(1).undeploy(MDB_DEPLOY);
        container(1).stop();
    }
}
