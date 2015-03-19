package org.jboss.qa.hornetq.test.journalexport;


import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtils;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Set of journal export/import tests.
 *
 * Tests for HornetQ journal export tool. These tests send messages with various properties to HornetQ, let HornetQ to write
 * them into the journal, shut down the server, start it again and read the message and validate its properties (and if the
 * export/import worked in the first place).
 */
@RunWith(Arquillian.class)
public class JournalExportImportTestCase extends HornetQTestCase {

    private static final long RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    private static final String EXPORTED_JOURNAL_FILE_NAME = "journal_export.xml";

    private static final String TEST_QUEUE = "TestQueue";
    private static final String TEST_QUEUE_NAME = "jms/queue/" + TEST_QUEUE;

    /**
     * Exporting message with null value in its properties
     *
     * @see <a href="https://bugzilla.redhat.com/show_bug.cgi?id=1121685">BZ1121685</a>
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testExportImportMessageWithNullProperty() throws Exception {
        controller.start(CONTAINER1_NAME);
        prepareServer(container(1));

        Context ctx = null;
        Connection conn = null;
        Session session = null;

        try {
            ctx = getContext(CONTAINER1_NAME);
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup(getConnectionFactoryName());
            conn = factory.createConnection();

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue testQueue = (Queue) ctx.lookup(TEST_QUEUE_NAME);
            MessageProducer producer = session.createProducer(testQueue);

            Message msg = session.createTextMessage("Test text");
            msg.setStringProperty("test_property", null);
            producer.send(msg);
        } finally {
            closeJmsConnection(ctx, conn, session);
        }

        stopServer(CONTAINER1_NAME);

        boolean exported = journalExportImportUtils.exportHornetQJournal(CONTAINER1_INFO, EXPORTED_JOURNAL_FILE_NAME);
        assertTrue("Journal should be exported successfully", exported);

        // delete the journal file before we import it again
        deleteDataFolder(CONTAINER1_INFO.getJbossHome(), CONTAINER1_NAME);
        controller.start(CONTAINER1_NAME);

        boolean imported = journalExportImportUtils.importHornetQJournal(CONTAINER1_INFO, EXPORTED_JOURNAL_FILE_NAME);
        assertTrue("Journal should be imported successfully", imported);

        Message received;
        try {
            ctx = getContext(CONTAINER1_NAME);
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup(getConnectionFactoryName());
            conn = factory.createConnection();
            conn.start();

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue testQueue = (Queue) ctx.lookup(TEST_QUEUE_NAME);
            MessageConsumer consumer = session.createConsumer(testQueue);

            received = consumer.receive(RECEIVE_TIMEOUT);
        } finally {
            closeJmsConnection(ctx, conn, session);
        }

        assertNotNull("Received message should not be null", received);
        assertNull("Tested property should be null", received.getStringProperty("test_property"));
        assertTrue("Test message should be received as proper message type", received instanceof TextMessage);
        assertEquals("Test message body should be preserved", "Test text", ((TextMessage) received).getText());
    }

    private void prepareServer(final Container container) {
        JMSOperations ops = container.getJmsOperations();
        ops.createQueue(TEST_QUEUE, TEST_QUEUE_NAME, true);
        ops.close();
    }

    private void closeJmsConnection(final Context ctx, final Connection connection, final Session session)
            throws JMSException, NamingException {

        if (session != null) {
            session.close();
        }

        if (connection != null) {
            connection.stop();
            connection.close();
        }

        if (ctx != null) {
            ctx.close();
        }
    }

}
