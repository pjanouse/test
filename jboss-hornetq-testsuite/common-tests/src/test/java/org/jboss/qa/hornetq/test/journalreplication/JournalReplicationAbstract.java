package org.jboss.qa.hornetq.test.journalreplication;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.journalreplication.utils.JMSUtil;
import org.jboss.qa.hornetq.test.journalreplication.utils.NetworkProblemController;
import org.jboss.qa.hornetq.test.journalreplication.utils.ServerUtil;
import org.jboss.qa.hornetq.test.journalreplication.utils.ThreadUtil;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.specific.JournalReplicationPrepare;
import org.jboss.qa.hornetq.tools.ControllableProxy;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter NETWORK FAILURE TESTING IN REPLICATED JOURNAL - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-replicated-journal-network-failures/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 */
@RunWith(Arquillian.class)
@Prepare("JournalReplicationPrepare")
public abstract class JournalReplicationAbstract extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(JournalReplicationAbstract.class);

    private static final int MESSAGES_NUM = 100;

    private static final int ACKNOWLEDGE_EVERY = 10;

    private static final int RETRY_MAX_ATTEMPTS = 3;
    private static final int RETRY_SLEEP_SECS = 2;

    private final String SERVER_IP_LIVE = container(1).getHostname();
    private final String SERVER_IP_BACKUP = container(2).getHostname();

    private final int MESSAGING_TO_LIVE_REAL_PORT = container(1).getHornetqPort();
    private final int MESSAGING_TO_BACKUP_REAL_PORT = container(2).getHornetqPort();

    private static final boolean NON_TRANSACTED = false;

    enum NetworkFailurePoint {

        NONE, INITIAL_REPLICATION, POST_INITIAL_REPLICATION
    }

    /**
     * @tpTestDetails Start 2 servers in dedicated HA topology with replicated
     * journal. Start Live server and send messages to queue. Start backup
     * server. Start consumer on live server which reads messages. Disconnect
     * network between live and backup and kill live server so consumer and
     * producer failover to backup. Tested with AIO/NIO for journal-type and
     * IO/NIO for connectors.
     *
     *
     * @tpProcedure <ul>
     * <li>start live server in dedicated HA topology with replicated
     * journal</li>
     * <li>send messages to queue</li>
     * <li>start backup server</li>
     * <li>start consumer for live server</li>
     * <li>disconnect network between live and backup</li>
     * <li>kill live server</li>
     * <li>wait until clients failover</li>
     * </ul>
     *
     * @tpPassCrit Receiver will receive all messages which were sent.
     */
    @Test/*(timeout=180000) = 3 minutes see https://issues.jboss.org/browse/ARQ-1071*/

    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void journalReplicationWithoutNetworkProblemTest() throws Exception {
        testCore(NetworkFailurePoint.NONE);
    }
    /**
     * @tpTestDetails Start 2 servers in dedicated HA topology with replicated
     * journal. Start Live server and send messages to queue. Start backup
     * server and during initial journal synchronization with live server
     * disconnect and reconnect network between a few times. Start consumer on
     * live server which reads messages and kill live server so consumer and
     * producer failover to backup. Tested with AIO/NIO for journal-type and
     * IO/NIO for connectors.
     *
     *
     * @tpProcedure <ul>
     * <li>start live server in dedicated HA topology with replicated journal</li>
     * <li>send messages to queue</li>
     * <li>start backup server and during synchronization disconnect and reconnect network between live and backup a few times</li>
     * <li>start consumer for live server</li>
     * <li>disconnect network between live and backup</li>
     * <li>kill live server</li>
     * <li>wait until clients failover</li>
     * </ul>
     *
     * @tpPassCrit Receiver will receive all messages which were sent.
     */
    @Test/*(timeout=180000) = 3 minutes see https://issues.jboss.org/browse/ARQ-1071*/
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void networkProblemsWhileInitialReplicationTest() throws Exception {
        testCore(NetworkFailurePoint.INITIAL_REPLICATION);
    }

        /**
     * @tpTestDetails Start 2 servers in dedicated HA topology with replicated
     * journal. Start Live server and send messages to queue. Start backup
     * server. Start consumer on live server which reads messages and disconnect
     * and reconnect network between live and backup a few times. Kill live
     * server so consumer and producer failover to backup. Tested with AIO/NIO
     * for journal-type and IO/NIO for connectors.
     *
     *
     * @tpProcedure <ul>
     * <li>start live server in dedicated HA topology with replicated journal</li>
     * <li>send messages to queue</li>
     * <li>start backup server </li>
     * <li>start consumer for live server</li>
     * <li>disconnect and reconnect network between live and backup a few times</li>
     * <li>kill live server</li>
     * <li>wait until clients failover</li>
     * </ul>
     *
     * @tpPassCrit Receiver will receive all messages which were sent.
     */
    @Test/*(timeout=180000) = 3 minutes see https://issues.jboss.org/browse/ARQ-1071*/

    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void networkProblemsAfterInitialReplicationTest() throws Exception {
        testCore(NetworkFailurePoint.POST_INITIAL_REPLICATION);
    }

    public void testCore(NetworkFailurePoint testPoint) throws Exception {
        ProducerTransAck producer = null;
        Connection connection = null;

        try {
            ControllableProxy proxyToLive = createProxyToLive();
            proxyToLive.start();

            ControllableProxy proxyToBackup = createProxyToBackup();
            proxyToBackup.start();

            startLiveServer();

            producer = sendMessagesToLive();

            if (testPoint == NetworkFailurePoint.INITIAL_REPLICATION) {
                // random 4-6
                int initialDelay = new Random().nextInt(2) + 4;
                new NetworkProblemController(proxyToLive, initialDelay).start();
                new NetworkProblemController(proxyToBackup, initialDelay).start();
            }

            startBackupServer();

        /*
         * replication start point and network failures
         */
            log.info("Waiting additional " + 60 + " s");
            sleepSeconds(60);

            Context context = container(1).getContext();
            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup("jms/" + PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME);
            connection = connectionFactory.createConnection();
            connection.start();
            Queue queue = (Queue) context.lookup(PrepareConstants.QUEUE_JNDI);
            Session session = connection.createSession(NON_TRANSACTED, Session.CLIENT_ACKNOWLEDGE);

            MessageConsumer receiver = session.createConsumer(queue);

            if (testPoint == NetworkFailurePoint.POST_INITIAL_REPLICATION) {
                // random 1-3
                int initialDelay = new Random().nextInt(2) + 1;
                new NetworkProblemController(proxyToLive, initialDelay).start();
                new NetworkProblemController(proxyToBackup, initialDelay).start();
            }

            boolean isKillTrigered = false;
            int messagesRecievedNum = 0;
            int messagesAcknowledgedNum = 0;

            while (messagesRecievedNum < MESSAGES_NUM) {
                Message message = receiveMessage(receiver, RETRY_MAX_ATTEMPTS, RETRY_SLEEP_SECS);

                if (message == null) {
                    log.info("Got null message. Breaking...");
                    break;
                } else {
                    messagesRecievedNum++;
                    log.info("Received [" + messagesRecievedNum + "] messages...");
                }

                if (messagesRecievedNum % ACKNOWLEDGE_EVERY == 0) {
                    if (messagesRecievedNum > MESSAGES_NUM / 2 && !isKillTrigered) {
                        proxyToLive.stop();
                        proxyToBackup.stop();

                        container(1).kill();

                        isKillTrigered = true;

                        sleepSeconds(10);
                    }

                    boolean isAcknowledged = acknowlegeMessage(message, RETRY_MAX_ATTEMPTS, RETRY_SLEEP_SECS);

                    if (!isAcknowledged) {
                        log.error("Messages were not acknowledged. Breaking...");
                        break;
                    }
                    messagesAcknowledgedNum += ACKNOWLEDGE_EVERY;
                }
            }

            assertEquals("Incorrect number received:", MESSAGES_NUM, messagesAcknowledgedNum);

        } finally {
            if (producer != null) {
                producer.stopSending();
                producer.join();
            }
            if (connection != null) {
                connection.close();
            }
        }

    }

    private void startLiveServer() {
        container(1).start();
    }

    private void startBackupServer() {
        container(2).start();
    }

    private void killLiveServer() {
        ServerUtil.killServer(getLiveServerIP());
    }

    private void sleepSeconds(int seconds) {
        ThreadUtil.sleepSeconds(seconds);
    }

    private ProducerTransAck sendMessagesToLive() {
//		SoakProducerClientAck producerToLive = createSenderToLive(MESSAGES_NUM);
//
//		producerToLive.run();

        ProducerTransAck p = createSenderToLive(MESSAGES_NUM);
        p.setMessageBuilder(new TextMessageBuilder(300 * 1024));
        p.start();
        return p;
    }

    private Message receiveMessage(MessageConsumer receiver, int maxRetryNum, int retrySleepSeconds) {
        return JMSUtil.receiveMessage(receiver, maxRetryNum, retrySleepSeconds);
    }

    private boolean acknowlegeMessage(Message message, int maxRetryNum, int retrySleepSeconds) {
        return JMSUtil.acknowlegeMessage(message, maxRetryNum, retrySleepSeconds);
    }

    public ProducerTransAck createSenderToLive(int MESSAGES_NUM) {
//		return new SoakProducerClientAck(
//				getLiveServerID(),
//				SERVER_IP_LIVE,
//				getJNDIPort(),
//				JNDI_QUEUE,
//				MESSAGES_NUM);
        return new ProducerTransAck(
                container(1),
                PrepareConstants.QUEUE_JNDI,
                MESSAGES_NUM);
    }

    public ControllableProxy createProxyToLive() {
        return new SimpleProxyServer(
                SERVER_IP_LIVE,
                MESSAGING_TO_LIVE_REAL_PORT,
                JournalReplicationPrepare.MESSAGING_TO_LIVE_PROXY_PORT);
    }

    public ControllableProxy createProxyToBackup() {
        return new SimpleProxyServer(
                SERVER_IP_BACKUP,
                MESSAGING_TO_BACKUP_REAL_PORT,
                JournalReplicationPrepare.MESSAGING_TO_BACKUP_PROXY_PORT);
    }

    public String getLiveServerIP() {
        return SERVER_IP_LIVE;
    }

}
