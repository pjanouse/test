package org.jboss.qa.artemis.test.journalexport;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import category.Functional;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
@Category(Functional.class)
public class JournalExportImportTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(JournalExportImportTestCase.class);

    private static final long RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    private String directoryWithJournal;
    private static final String EXPORTED_JOURNAL_FILE_NAME_PATTERN = "target/journal_export";

    @Before
    public void setUpTest() {
        directoryWithJournal = new StringBuilder(container(1).getServerHome())
                .append(File.separator).append("standalone")
                .append(File.separator).append("data")
                .append(File.separator).append("activemq")
                .toString();
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
    @Prepare("OneNode")
    public void testExportImportMessageWithNullProperty() throws Exception {

        Assume.assumeFalse(prepareCoordinator.getParams().containsKey(PrepareParams.DATABASE));

        container(1).start();

        logger.info("JOURNAL DIRECTORY: " + directoryWithJournal);

        JournalExportImportUtils journalExportImportUtils = container(1).getExportImportUtil();
        journalExportImportUtils.setPathToJournalDirectory(directoryWithJournal);

        Context ctx = null;
        Connection conn = null;
        Session session = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            conn = factory.createConnection();

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue testQueue = (Queue) ctx.lookup(PrepareConstants.QUEUE_JNDI);
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
        FileUtils.deleteDirectory(new File(directoryWithJournal));

        journalExportImportUtils.importJournal(EXPORTED_JOURNAL_FILE_NAME_PATTERN);
        container(1).start();
        Message received;
        try {
            ctx = container(1).getContext();
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            conn = factory.createConnection();
            conn.start();

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue testQueue = (Queue) ctx.lookup(PrepareConstants.QUEUE_JNDI);
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
    @Prepare("OneNode")
    public void testExportImportMessageWithNullPropertyUsingAdminOperation() throws Exception {

        Assume.assumeFalse(prepareCoordinator.getParams().containsKey(PrepareParams.DATABASE));

        container(1).start();

        Context ctx = null;
        Connection conn = null;
        Session session = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            conn = factory.createConnection();

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue testQueue = (Queue) ctx.lookup(PrepareConstants.QUEUE_JNDI);
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
        FileUtils.deleteDirectory(new File(directoryWithJournal, "bindings"));
        FileUtils.deleteDirectory(new File(directoryWithJournal, "journal"));
        FileUtils.deleteDirectory(new File(directoryWithJournal, "paging"));
        FileUtils.deleteDirectory(new File(directoryWithJournal, "largemessages"));

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

            Queue testQueue = (Queue) ctx.lookup(PrepareConstants.QUEUE_JNDI);
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

    /**
     * Exporting message with null using CLI operations
     *
     * @tpTestDetails Start single server. Send 50 messages of various types and sizes (normal, large)
     * to the destination on the server. Once sent, restart server in admin only mode. Export
     * journal, stop server, clean it, start server and import using CLI. Read the
     * messages from the destination.
     * @tpPassCrit All the test messages are successfully read and preserve all
     * their properties
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.REMOTE_CONNECTION_FACTORY_COMPRESSION, value = "false")
    })
    public void testExportImportAllTypeMessagesUsingAdminOperation() throws Exception {
        MessageBuilder messageBuilder = new ClientMixMessageBuilder(1, 1024 * 200);
        internalTestExportImportLargeMessagesUsingAdminOperation(messageBuilder);
    }

    /**
     * Exporting message with null using CLI operations
     *
     * @tpTestDetails Start single server. Send 50 messages of various types and sizes (normal, large)
     * to the destination on the server. Compress large messages. Once sent, restart server in admin only mode. Export
     * journal, stop server, clean it, start server and import using CLI. Read the
     * messages from the destination.
     * @tpPassCrit All the test messages are successfully read and preserve all
     * their properties
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.REMOTE_CONNECTION_FACTORY_COMPRESSION, value = "true")
    })
    public void testExportImportCompressedAllTypeMessagesUsingAdminOperation() throws Exception {
        MessageBuilder messageBuilder = new ClientMixMessageBuilder(1, 1024 * 200);
        internalTestExportImportLargeMessagesUsingAdminOperation(messageBuilder);
    }

    /**
     * Exporting message with null using CLI operations
     *
     * @tpTestDetails Start single server. Send 50 messages large TextMessages
     * to the destination on the server. Once sent, restart server in admin only mode. Export
     * journal, stop server, clean it, start server and import using CLI. Read the
     * messages from the destination.
     * @tpPassCrit All the test messages are successfully read and preserve all
     * their properties
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.REMOTE_CONNECTION_FACTORY_COMPRESSION, value = "false")
    })
    public void testExportImportLargeMessagesUsingAdminOperation() throws Exception {

        MessageBuilder messageBuilder = new TextMessageBuilder(1024 * 2000);

        internalTestExportImportLargeMessagesUsingAdminOperation(messageBuilder);
    }

    /**
     * Exporting message with null using CLI operations
     *
     * @tpTestDetails Start single server. Send 50 messages large TextMessages
     * to the destination on the server with enabled compression. Once sent, restart server in admin only mode. Export
     * journal, stop server, clean it, start server and import using CLI. Read the
     * messages from the destination.
     * @tpPassCrit All the test messages are successfully read and preserve all
     * their properties
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.REMOTE_CONNECTION_FACTORY_COMPRESSION, value = "true")
    })
    public void testExportImportCompressedLargeMessagesUsingAdminOperation() throws Exception {

        MessageBuilder messageBuilder = new TextMessageBuilder(1024 * 200);
        internalTestExportImportLargeMessagesUsingAdminOperation(messageBuilder);
    }

    private void internalTestExportImportLargeMessagesUsingAdminOperation(MessageBuilder messageBuilder) throws Exception {

        Assume.assumeFalse(prepareCoordinator.getParams().containsKey(PrepareParams.DATABASE));

        container(1).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, 50);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setCommitAfter(5);
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
        FileUtils.deleteDirectory(new File(directoryWithJournal, "bindings"));
        FileUtils.deleteDirectory(new File(directoryWithJournal, "journal"));
        FileUtils.deleteDirectory(new File(directoryWithJournal, "paging"));
        FileUtils.deleteDirectory(new File(directoryWithJournal, "largemessages"));

        container(1).start();
        ops = container(1).getJmsOperations();

        ops.importJournal(exportedJournalFile);
        ops.close();

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), PrepareConstants.QUEUE_JNDI, 5000, 10, 10);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);
        Assert.assertEquals("There is different number of sent and received messages. Received: "
                + receiver1.getListOfReceivedMessages().size() + ", Sent: " + producerToInQueue1.getListOfSentMessages().size()
                + ".", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

    }

}
