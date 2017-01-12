package org.jboss.qa.hornetq.test.journalexport;


import category.Functional;
import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.MixMessageBuilder;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
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
 * Tests for HornetQ journal export tool. These tests send messages with various properties to HornetQ, let HornetQ to write
 * them into the journal, shut down the server, start it again and read the message and validate its properties (and if the
 * export/import worked in the first place).
 */
@RunWith(Arquillian.class)
@Category(Functional.class)
public class JournalExportImportTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(JournalExportImportTestCase.class);

    private static final String BYTEMAN_SET_LONG_ID_VALUE = "6442451191";
    private static final String BYTEMAN_SET_LONG_ID_MSG = "Byteman is setting generated queue ID to: " + BYTEMAN_SET_LONG_ID_VALUE;
    private static final String BYTEMAN_SET_LONG_ID = "System.out.println(\"" + BYTEMAN_SET_LONG_ID_MSG + "\");$id = Long.parseLong(\"" + BYTEMAN_SET_LONG_ID_VALUE + "\");";

    private static final long RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    private static final String EXPORTED_JOURNAL_FILE_NAME = "target/journal_export.xml";

    private static final String LONG_TEST_QUEUE = "TestQueueLong";
    private static final String LONG_TEST_QUEUE_NAME = "jms/queue/" + LONG_TEST_QUEUE;

    /**
     * Exporting message with null value in its properties
     *
     * @see <a href="https://bugzilla.redhat.com/show_bug.cgi?id=1121685">BZ1121685</a>
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare("OneNode")
    public void testExportImportMessageWithNullProperty() throws Exception {

        container(1).start();

        final String directoryWithJournal = new StringBuilder(container(1).getServerHome())
                                                .append(File.separator).append("standalone")
                                                .append(File.separator).append("data")
                                                .toString();

        JournalExportImportUtils journalExportImportUtils = container(1).getExportImportUtil();
        journalExportImportUtils.setPathToJournalDirectory(directoryWithJournal);

        Context ctx = null;
        Connection conn = null;
        Session session = null;

        try {
            ctx = container(1). getContext();
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

        boolean exported = journalExportImportUtils.exportJournal(EXPORTED_JOURNAL_FILE_NAME);
        assertTrue("Journal should be exported successfully", exported);

        // delete the journal file before we import it again
        FileUtils.deleteDirectory(new File(directoryWithJournal));
        container(1).start();

        boolean imported = journalExportImportUtils.importJournal(EXPORTED_JOURNAL_FILE_NAME);
        assertTrue("Journal should be imported successfully", imported);

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
     * Exporting and importing a message sent to a queue with queue ID outside of integer range
     *
     * @see <a href="https://bugzilla.redhat.com/show_bug.cgi?id=1375295">BZ1375295</a>
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
        @BMRule(name = "Hornetq change generated queue ID value to be outside of integer range",
            targetClass = "org.hornetq.core.persistence.impl.journal.BatchingIDGenerator",
            targetMethod = "generateID",
            targetLocation = "WRITE $id",
            isAfter = true,
            action = BYTEMAN_SET_LONG_ID)
    })
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.PREPARE_DESTINATIONS, value = "false")
    })
    public void testExportImportMessageWithQueueWithLongID() throws Exception {

        container(1).start();

        logger.info("Installing Byteman rule before creating queue ...");
        RuleInstaller.installRule(this.getClass(), container(1).getHostname(), container(1).getBytemanPort());

        JMSOperations ops = container(1).getJmsOperations();
        ops.createQueue(LONG_TEST_QUEUE, LONG_TEST_QUEUE_NAME, true);

        logger.info("Uninstalling Byteman rules after creating queue ...");
        RuleInstaller.uninstallAllRules(container(1).getHostname(), container(1).getBytemanPort());

        JournalExportImportUtils journalExportImportUtils = container(1).getExportImportUtil();

        Context ctx = null;
        Connection conn = null;
        Session session = null;

        try {
            ctx = container(1). getContext();
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            conn = factory.createConnection();

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue testQueue = (Queue) ctx.lookup(LONG_TEST_QUEUE_NAME);
            MessageProducer producer = session.createProducer(testQueue);

            Message msg = session.createTextMessage("Test text");
            producer.send(msg);
        } finally {
            JMSTools.cleanupResources(ctx, conn, session);
        }

        container(1).stop();

        boolean exported = journalExportImportUtils.exportJournal(EXPORTED_JOURNAL_FILE_NAME);
        assertTrue("Journal should be exported successfully", exported);

        // delete the journal file before we import it again
        // a new queue with a new id would be created without the bindings journal so we can't delete the whole directory
        FileUtils.deleteDirectory(new File(getJournalDirectory(container(1)), "messagingjournal"));


        container(1).start();

        boolean imported = journalExportImportUtils.importJournal(EXPORTED_JOURNAL_FILE_NAME);
        assertTrue("Journal should be imported successfully", imported);

        Message received;
        try {
            ctx = container(1).getContext();
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            conn = factory.createConnection();
            conn.start();

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue testQueue = (Queue) ctx.lookup(LONG_TEST_QUEUE_NAME);
            MessageConsumer consumer = session.createConsumer(testQueue);

            received = consumer.receive(RECEIVE_TIMEOUT);
        } finally {
            JMSTools.cleanupResources(ctx, conn, session);
        }

        assertNotNull("Received message should not be null", received);
        assertTrue("Test message should be received as proper message type", received instanceof TextMessage);
        assertEquals("Test message body should be preserved", "Test text", ((TextMessage) received).getText());
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.REMOTE_CONNECTION_FACTORY_COMPRESSION, value = "false")
    })
    public void testExportImportLargeMessages() throws Exception {
        internalTestExportImportMessages(new MixMessageBuilder(1024 * 200));
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.REMOTE_CONNECTION_FACTORY_COMPRESSION, value = "true")
    })
    public void testExportImportCompressedLargeMessages() throws Exception {
        internalTestExportImportMessages(new MixMessageBuilder(1024 * 200));
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.REMOTE_CONNECTION_FACTORY_COMPRESSION, value = "false")
    })
    public void testExportImportNormalMessages() throws Exception {
        internalTestExportImportMessages(new MixMessageBuilder(1024));
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.REMOTE_CONNECTION_FACTORY_COMPRESSION, value = "true")
    })
    public void testExportImportCompressedNormalMessages() throws Exception {
        internalTestExportImportMessages(new MixMessageBuilder(1024));
    }

    @After
    public void deleteJournalFiles() {
        try {
            FileUtils.deleteDirectory(getJournalDirectory(container(1)));
        } catch (Exception e) {
            logger.error("Cannot delete journals directory. Hope it is ok and continue test, in case of failure this can be cause.", e);
        }
    }

    private void internalTestExportImportMessages(MessageBuilder messageBuilder) throws Exception {

        container(1).start();

        JournalExportImportUtils journalExportImportUtils = container(1).getExportImportUtil();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, 50);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.start();
        producerToInQueue1.join();

        container(1).stop();

        boolean exported = journalExportImportUtils.exportJournal(EXPORTED_JOURNAL_FILE_NAME);
        assertTrue("Journal should be exported successfully", exported);


        // delete the journal file before we import it again
        FileUtils.deleteDirectory(getJournalDirectory(container(1)));

        container(1).start();

        boolean imported = journalExportImportUtils.importJournal(EXPORTED_JOURNAL_FILE_NAME);
        assertTrue("Journal should be imported successfully", imported);

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), PrepareConstants.QUEUE_JNDI, 5000, 10, 10);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);
        Assert.assertEquals("There is different number of sent and received messages. Received: "
            + receiver1.getListOfReceivedMessages().size() + ", Sent: " + producerToInQueue1.getListOfSentMessages().size()
            + ".", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

    }

    private File getJournalDirectory(Container container) {

        String path = container.getServerHome() + File.separator + "standalone" + File.separator + "data" + File.separator + "messagingjournal";
        return new File(path);
    }
}
