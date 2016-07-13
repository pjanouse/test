package org.jboss.qa.artemis.test.journalexport;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Set of journal export/import tests.
 *
 * @tpChapter Functional testing
 * @tpSubChapter JOURNAL XML EXPORT/IMPORT TOOL
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 * @tpTestCaseDetails Tests for ActiveMQ journal export tool. These tests send
 * messages with various properties to ActiveMQ, let ActiveMQ to write them into
 * the journal, shut down the server, start it again and read the message and
 * validate its properties (and if the export/import worked in the first place).
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class JournalExportImportTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(JournalExportImportTestCase.class);

    private static final long RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    private static final String DIRECTORY_WITH_JOURNAL = new File("target/journal-directory-for-import-export-testcase").getAbsolutePath();
    private static final String EXPORTED_JOURNAL_FILE_NAME_PATTERN = "journal_export";

    private static final String TEST_QUEUE = "testQueue";
    private static final String TEST_QUEUE_NAME = "jms/queue/" + TEST_QUEUE;

    @After
    @Before
    public void stopAllServers() {
        container(1).stop();
    }

    /**
     * Exporting message with null value in its properties
     *
     * @tpTestDetails Start single server. Send text message with null property
     * to the destination on the server. Once sent, shut the server down. Export
     * journal and then import it to the clean server instance. Read the
     * messages from the destination.
     * @tpProcedure <ul>
     * <li>Start server with single queue deployed.</li>
     * <li>Connect to the server with the client and send test messages to the
     * queue.</li>
     * <li>Shut the server down and export its HornetQ journal to XML file.</li>
     * <li>Clean the server directories and import journal again.</li>
     * <li>Start the server</li>
     * <li>Read the messages from the queue.</li>
     * </ul>
     * @tpPassCrit All the test messages are successfully read and preserve all
     * their properties
     * @tpInfo <a href="https://bugzilla.redhat.com/show_bug.cgi?id=1121685">BZ1121685</a>
     * @see <a href="https://bugzilla.redhat.com/show_bug.cgi?id=1121685">BZ1121685</a>
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testExportImportMessageWithNullProperty() throws Exception {

        prepareServer(container(1));

        container(1).start();

        JournalExportImportUtils journalExportImportUtils = container(1).getExportImportUtil();
        journalExportImportUtils.setPathToJournalDirectory(DIRECTORY_WITH_JOURNAL);

        Context ctx = null;
        Connection conn = null;
        Session session = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            conn = factory.createConnection();

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue testQueue = (Queue) ctx.lookup(TEST_QUEUE_NAME);
            MessageProducer producer = session.createProducer(testQueue);

            Message msg = session.createTextMessage("Test text");
            msg.setStringProperty("test_property", null);
            producer.send(msg);
        } finally {
            JMSTools.cleanupResources(ctx, conn, session);
        }

        container(1).stop();

        journalExportImportUtils.exportJournal(EXPORTED_JOURNAL_FILE_NAME_PATTERN);

        // delete the journal file before we import it again
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL));

        journalExportImportUtils.importJournal(EXPORTED_JOURNAL_FILE_NAME_PATTERN);
        container(1).start();
        Message received;
        try {
            ctx = container(1).getContext();
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            conn = factory.createConnection();
            conn.start();

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue testQueue = (Queue) ctx.lookup(TEST_QUEUE_NAME);
            MessageConsumer consumer = session.createConsumer(testQueue);

            received = consumer.receive(RECEIVE_TIMEOUT);
        } finally {
            JMSTools.cleanupResources(ctx, conn, session);
        }

        assertNotNull("Received message should not be null", received);
        assertNull("Tested property should be null", received.getStringProperty("test_property"));
        assertTrue("Test message should be received as proper message type", received instanceof TextMessage);
        assertEquals("Test message body should be preserved", "Test text", ((TextMessage) received).getText());
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testExportImportMessageWithNullPropertyUsingAdminOperation() throws Exception {

        prepareServer(container(1));

        container(1).start();

        Context ctx = null;
        Connection conn = null;
        Session session = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            conn = factory.createConnection();

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue testQueue = (Queue) ctx.lookup(TEST_QUEUE_NAME);
            MessageProducer producer = session.createProducer(testQueue);

            Message msg = session.createTextMessage("Test text");
            msg.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
            msg.setStringProperty("test_property", null);
            producer.send(msg);
        } finally {
            JMSTools.cleanupResources(ctx, conn, session);
        }

        container(1).stop();

        //start in admin-only mode
        container(1).startAdminOnly();
        JMSOperations ops = container(1).getJmsOperations();
        String exportedJournalFile = ops.exportJournal();
        ops.close();
        container(1).stop();

        // delete the journal file before we import it again
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL, "bindings"));
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL, "journal"));
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL, "paging"));
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL, "largemessages"));

        container(1).start();
        ops = container(1).getJmsOperations();

        ops.importJournal(exportedJournalFile);
        ops.close();

        Message received;
        try {
            ctx = container(1).getContext();
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            conn = factory.createConnection();
            conn.start();

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue testQueue = (Queue) ctx.lookup(TEST_QUEUE_NAME);
            MessageConsumer consumer = session.createConsumer(testQueue);

            received = consumer.receive(RECEIVE_TIMEOUT);
        } finally {
            JMSTools.cleanupResources(ctx, conn, session);
        }

        assertNotNull("Received message should not be null", received);
        assertNull("Tested property should be null", received.getStringProperty("test_property"));
        assertTrue("Test message should be received as proper message type", received instanceof TextMessage);
        assertEquals("Test message body should be preserved", "Test text", ((TextMessage) received).getText());
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testExportImportLargeMessagesUsingAdminOperation() throws Exception {
        internalTestExportImportLargeMessagesUsingAdminOperation(false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testExportImportCompressedLargeMessagesUsingAdminOperation() throws Exception {
        internalTestExportImportLargeMessagesUsingAdminOperation(true);
    }

    private void internalTestExportImportLargeMessagesUsingAdminOperation(boolean compressMessages) throws Exception {

        prepareServer(container(1), compressMessages);

        container(1).start();

        MessageBuilder messageBuilder = new TextMessageBuilder(1024 * 200);

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), TEST_QUEUE_NAME, 20);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.start();
        producerToInQueue1.join();

        container(1).stop();

        container(1).startAdminOnly();

        JMSOperations ops = container(1).getJmsOperations();
        String exportedJournalFile = ops.exportJournal();
        ops.close();
        container(1).stop();

        // delete the journal file before we import it again
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL, "bindings"));
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL, "journal"));
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL, "paging"));
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL, "largemessages"));

        container(1).start();
        ops = container(1).getJmsOperations();

        ops.importJournal(exportedJournalFile);
        ops.close();

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), TEST_QUEUE_NAME, 5000, 10, 10);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);
        Assert.assertEquals("There is different number of sent and received messages. Received: "
                + receiver1.getListOfReceivedMessages().size() + ", Sent: " + producerToInQueue1.getListOfSentMessages().size()
                + ".", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

    }

    private void prepareServer(final Container container) {
        prepareServer(container, false);
    }

    private void prepareServer(final Container container, boolean compressLargeMessages) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String groupAddress = "233.6.88.3";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations ops = container.getJmsOperations();
        ops.createQueue(TEST_QUEUE, TEST_QUEUE_NAME, true);
        ops.setJournalDirectory(DIRECTORY_WITH_JOURNAL);
        ops.setLargeMessagesDirectory(DIRECTORY_WITH_JOURNAL);
        ops.setBindingsDirectory(DIRECTORY_WITH_JOURNAL);
        ops.setPagingDirectory(DIRECTORY_WITH_JOURNAL);

        ops.removeBroadcastGroup(broadCastGroupName);
        ops.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
        ops.removeDiscoveryGroup(discoveryGroupName);
        ops.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, groupAddress);
        ops.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        ops.disableSecurity();
        ops.removeClusteringGroup(clusterGroupName);
        ops.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        ops.setCompressionOnConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, compressLargeMessages);

        ops.close();
        container.stop();
    }

    @After
    public void deleteJournalFiles() {
        try {
            FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL));
        } catch (Exception e) {
            logger.error("Cannot delete journals directory. Hope it is ok and continue test, in case of failure this can be cause.", e);
        }
    }

}
