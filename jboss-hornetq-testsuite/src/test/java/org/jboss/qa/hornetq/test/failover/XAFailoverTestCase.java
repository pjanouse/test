package org.jboss.qa.hornetq.test.failover;
//todo add to test plan to mojo
// todo report that first xa transaction after failover fails

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.XAConsumerTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mnovak on 2/24/14.
 */
public class XAFailoverTestCase extends HornetQTestCase {


    private static final Logger logger = Logger.getLogger(XAFailoverTestCase.class);

    public static String queueNamePrefix = "testQueue";
    public static String queueJndiNamePrefix = "jms/queue/" + queueNamePrefix;
    public static int NUMBER_OF_DESTINATIONS = 1;

    private static String objectStoreDir = "ObjectStore";
    private String nodeIdentifier = objectStoreDir; // keep this the same as object store dir because recovery manager does not reflect this

    private long hornetqTransactionTimeout = 120000;

    ///////////////////CLEAN UP ///////////////
    @BeforeClass
    public static void cleanUp() {
        File objectStoreDirFile = new File(objectStoreDir);
        if (objectStoreDirFile.exists()) {
            deleteFolder(objectStoreDirFile);
        }
    }

    @After
    public void stopServers()   {

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);
    }

    //////////////////// TESTS WITH MULTIPLE CONSUMERS ///////////////////////////
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill after xa start.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaStart",
            targetLocation = "EXIT",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumersKillAfterStart() throws Exception {
        testFailoverWithXAConsumers();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill before xa end.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaEnd",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumersKillBeforeEnd() throws Exception {
        testFailoverWithXAConsumers();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill after end.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaEnd",
            targetLocation = "EXIT",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumersKillAfterEnd() throws Exception {
        testFailoverWithXAConsumers();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill before prepare is written to journal.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaPrepare",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumersKillBeforePrepare() throws Exception {
        testFailoverWithXAConsumers();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill after prepare is written to journal.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaPrepare",
            targetLocation = "EXIT",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumersKillAfterPrepare() throws Exception {
        testFailoverWithXAConsumers();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill before commit is written to journal.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaCommit",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumersKillBeforeCommit() throws Exception {
        testFailoverWithXAConsumers();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill before commit is written to journal.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaCommit",
            targetLocation = "EXIT",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumersKillAfterCommit() throws Exception {
        testFailoverWithXAConsumers();
    }

    /////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////
    /////////////////////////// TESTS WITH ONE CONSUMER ///////////////////////////

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill after xa start.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaStart",
            targetLocation = "EXIT",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumerKillAfterStart() throws Exception {
        testFailoverWithXAConsumer();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill before xa end.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaEnd",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumerKillBeforeEnd() throws Exception {
        testFailoverWithXAConsumer();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill after end.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaEnd",
            targetLocation = "EXIT",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumerKillAfterEnd() throws Exception {
        testFailoverWithXAConsumer();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill before prepare is written to journal.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaPrepare",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumerKillBeforePrepare() throws Exception {
        testFailoverWithXAConsumer();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill after prepare is written to journal.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaPrepare",
            targetLocation = "EXIT",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumerKillAfterPrepare() throws Exception {
        testFailoverWithXAConsumer();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill before commit is written to journal.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaCommit",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumerKillBeforeCommit() throws Exception {
        testFailoverWithXAConsumer();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill before commit is written to journal.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaCommit",
            targetLocation = "EXIT",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailoverWithXAConsumerKillAfterCommit() throws Exception {
        testFailoverWithXAConsumer();
    }

    /////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////

    private void testFailoverWithXAConsumer() throws Exception {

        boolean shutdown = false;
        int numberOfMessagesToSend = 1000;

        prepareLiveServer(CONTAINER1, JOURNAL_DIRECTORY_A);
        prepareBackupServer(CONTAINER2, JOURNAL_DIRECTORY_A);

        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        ProducerTransAck p = new ProducerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix + "0", numberOfMessagesToSend);
        MessageBuilder messageBuilder = new TextMessageBuilder(1);
        messageBuilder.setAddDuplicatedHeader(true);
        p.setMessageBuilder(messageBuilder);
        p.setMessageVerifier(messageVerifier);
        p.setCommitAfter(100);
        p.setTimeout(0);
        p.start();
        p.join();

        XAConsumerTransAck c = new XAConsumerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix + "0");
        c.setCommitAfter(10);
        c.setMessageVerifier(messageVerifier);
        c.start();

        List<Client> receivers = new ArrayList<Client>();
        receivers.add(c);
        waitForReceiversUntil(receivers, 500, 60000);

        if (!shutdown) {
            logger.warn("########################################");
            logger.warn("Kill live server");
            logger.warn("########################################");
            RuleInstaller.installRule(this.getClass(), getHostname(CONTAINER1), getBytemanPort(CONTAINER1));
            controller.kill(CONTAINER1);
        } else {
            logger.warn("########################################");
            logger.warn("Shutdown live server");
            logger.warn("########################################");
            stopServer(CONTAINER1);
        }

        logger.warn("Wait some time to give chance backup to come alive and clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.", waitHornetQToAlive(getHostname(CONTAINER2), getHornetqPort(CONTAINER2), 300000));

        c.join();

        messageVerifier.verifyMessages();

        logger.info("Get information about transactions from HQ:");

        long timeout = 180000;
        long startTime = System.currentTimeMillis();
        int numberOfPreparedTransaction = 100;
        JMSOperations jmsOperations = getJMSOperations(CONTAINER2);
        while (numberOfPreparedTransaction > 0 && System.currentTimeMillis() - startTime < timeout) {
            numberOfPreparedTransaction = jmsOperations.getNumberOfPreparedTransaction();
            Thread.sleep(1000);
        }
        jmsOperations.close();

        Assert.assertEquals("Number of send and received messages is different.", numberOfMessagesToSend, c.getListOfReceivedMessages().size());
        Assert.assertEquals("Number of prepared transactions must be 0", 0, numberOfPreparedTransaction);

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }

    private void testFailoverWithXAConsumers() throws Exception {

        boolean shutdown = false;
        int numberOfMessagesToSend = 5000;
        int numberOfConsumers = 5;

        prepareLiveServer(CONTAINER1, JOURNAL_DIRECTORY_A);
        prepareBackupServer(CONTAINER2, JOURNAL_DIRECTORY_A);

        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        ProducerTransAck p = new ProducerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix + "0", numberOfMessagesToSend);
        MessageBuilder messageBuilder = new TextMessageBuilder(1);
        messageBuilder.setAddDuplicatedHeader(true);
        p.setMessageBuilder(messageBuilder);
        p.setMessageVerifier(messageVerifier);
        p.setCommitAfter(100);
        p.setTimeout(0);
        p.start();
        p.join();

        List<Client> listOfReceivers = new ArrayList<Client>();
        for (int i = 0; i < numberOfConsumers; i++) {
            XAConsumerTransAck c = new XAConsumerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix + "0");
//            c.setMessageVerifier(messageVerifier);
            c.setCommitAfter(10);
            c.start();
            listOfReceivers.add(c);
        }

        waitForReceiversUntil(listOfReceivers, 500, 60000);

        if (!shutdown) {
            logger.warn("########################################");
            logger.warn("Kill live server");
            logger.warn("########################################");
            RuleInstaller.installRule(this.getClass(), getHostname(CONTAINER1), getBytemanPort(CONTAINER1));
            controller.kill(CONTAINER1);
        } else {
            logger.warn("########################################");
            logger.warn("Shutdown live server");
            logger.warn("########################################");
            stopServer(CONTAINER1);
        }

        logger.warn("Wait some time to give chance backup to come alive and clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.", waitHornetQToAlive(getHostname(CONTAINER2), getHornetqPort(CONTAINER2), 300000));

        waitForClientsToFinish(listOfReceivers, 300000);

        for (Client c : listOfReceivers) {
            messageVerifier.addReceivedMessages(((XAConsumerTransAck) c).getListOfReceivedMessages());
        }

        boolean isMessageVerificationOk = messageVerifier.verifyMessages();

        logger.info("Get information about transactions from HQ:");
        long timeout = 180000;
        long startTime = System.currentTimeMillis();
        int numberOfPreparedTransaction = 100;
        JMSOperations jmsOperations = getJMSOperations(CONTAINER2);
        while (numberOfPreparedTransaction > 0 && System.currentTimeMillis() - startTime < timeout) {
            numberOfPreparedTransaction = jmsOperations.getNumberOfPreparedTransaction();
            Thread.sleep(1000);
        }
        jmsOperations.close();

        Assert.assertTrue("Verification of received messages failed. Check logs for more details.", isMessageVerificationOk);
        Assert.assertEquals("Number of prepared transactions must be 0", 0, numberOfPreparedTransaction);

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill before commit is written to journal.",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "xaCommit",
            action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    public void testFailFirstTransactionOnBackup() throws Exception {
        int numberOfMessagesToSend = 1000;

        prepareLiveServer(CONTAINER1, JOURNAL_DIRECTORY_A);
        prepareBackupServer(CONTAINER2, JOURNAL_DIRECTORY_A);

        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        ProducerTransAck p = new ProducerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix + "0", numberOfMessagesToSend);
        MessageBuilder messageBuilder = new TextMessageBuilder(1);
        messageBuilder.setAddDuplicatedHeader(true);
        p.setMessageBuilder(messageBuilder);
        p.setMessageVerifier(messageVerifier);
        p.setCommitAfter(100);
        p.setTimeout(0);
        p.start();
        p.join();

        XAConsumerTransAck c = new XAConsumerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix + "0");
        c.setCommitAfter(10);
        c.setMessageVerifier(messageVerifier);
        c.start();

        List<Client> receivers = new ArrayList<Client>();
        receivers.add(c);
        waitForReceiversUntil(receivers, 500, 60000);

        logger.warn("########################################");
        logger.warn("Kill live server");
        logger.warn("########################################");
        RuleInstaller.installRule(this.getClass(), getHostname(CONTAINER1), getBytemanPort(CONTAINER1));
        controller.kill(CONTAINER1);

        logger.warn("Wait some time to give chance backup to come alive and clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.", waitHornetQToAlive(getHostname(CONTAINER2), getHornetqPort(CONTAINER2), 300000));

        // wait for clients to receive more messages from backup
        int numberOfReceivedMessages = c.getListOfReceivedMessages().size();
        while (numberOfReceivedMessages >= c.getListOfReceivedMessages().size()) {
            Thread.sleep(500);
        }

        logger.info("Get information about transactions from HQ after failover to backup and recovery passed.:");
        int numberOfPreparedTransaction = 100;
        JMSOperations jmsOperations = getJMSOperations(CONTAINER2);
        numberOfPreparedTransaction = jmsOperations.getNumberOfPreparedTransaction();
        String result = jmsOperations.listPreparedTransaction();
        jmsOperations.close();

        c.join();

        messageVerifier.verifyMessages();

        Assert.assertEquals("Number of send and received messages is different.", numberOfMessagesToSend, c.getListOfReceivedMessages().size());
        Assert.assertTrue("Number of prepared transactions must be 0 or 1 after failover to backup but it's " + numberOfPreparedTransaction + ". If there is just one " +
                "consumer then after failover there can be max 1 transaction in prepared state. List of prepared transactions after failover: " + result
                , 2 < numberOfPreparedTransaction);

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);
    }

    private void waitForClientsToFinish(List<Client> listOfReceivers, long timeout) throws InterruptedException {

        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeout) {

            boolean isFinished = true;
            // check receivers
            for (Thread receiverThread : listOfReceivers) {
                receiverThread.join(timeout);
                if (receiverThread.isAlive()) {
                    isFinished = false;
                }
            }

            if (isFinished) {
                return;
            }
            logger.info("Client did not finish yet - wait 1000ms.");
            Thread.sleep(1000);
        }
        Assert.fail("Clients did not finished in timeout: " + timeout + ". Check logs what happened.");
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName    Name of the container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     */
    protected void prepareLiveServer(String containerName, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFactoryType(connectionFactoryName, "XA_GENERIC");
        jmsAdminOperations.setTransactionTimeout(hornetqTransactionTimeout);

        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        jmsAdminOperations.close();

        controller.stop(containerName);

    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    protected void prepareBackupServer(String containerName, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setAllowFailback(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);
        jmsAdminOperations.setFactoryType(connectionFactoryName, "XA_GENERIC");
        jmsAdminOperations.setTransactionTimeout(hornetqTransactionTimeout);

        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.close();

        controller.stop(containerName);
    }

}
