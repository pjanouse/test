package org.jboss.qa.artemis.test.journalexport;

import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.as.controller.client.ModelControllerClient;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP_ADDR;
import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUCCESS;
import org.jboss.dmr.ModelNode;
import static org.jboss.qa.hornetq.HornetQTestCase.getArquillianDescriptor;
import org.jboss.qa.hornetq.tools.ActiveMQAdminOperationsEAP7;

import static org.junit.Assert.*;
import org.junit.Ignore;

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
 *
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class JournalExportImportTestCase extends HornetQTestCase {

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
     * @see
     * <a href="https://bugzilla.redhat.com/show_bug.cgi?id=1121685">BZ1121685</a>
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
     * @tpInfo
     * <a href="https://bugzilla.redhat.com/show_bug.cgi?id=1121685">BZ1121685</a>
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
            closeJmsConnection(ctx, conn, session);
        }

        container(1).stop();

        journalExportImportUtils.exportJournal(container(1), EXPORTED_JOURNAL_FILE_NAME_PATTERN);

        // delete the journal file before we import it again
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL));

        journalExportImportUtils.importJournal(container(1), EXPORTED_JOURNAL_FILE_NAME_PATTERN);
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
            closeJmsConnection(ctx, conn, session);
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
            closeJmsConnection(ctx, conn, session);
        }

        container(1).stop();

        Map<String, String> containerProperties = null;
        for (GroupDef groupDef : getArquillianDescriptor().getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(container(1).getName())) {
                    containerProperties = containerDef.getContainerProperties();
                }
            }
        }

        containerProperties.put("adminOnly", "true");

        //start in admin-only mode
        container(1).start(containerProperties);
        JMSOperations ops = container(1).getJmsOperations();
        String exportedJournalFile = ops.exportJournal();
        ops.close();
        container(1).stop();

        // delete the journal file before we import it again
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL,"bindings"));
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL,"journal"));
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL,"paging"));
        FileUtils.deleteDirectory(new File(DIRECTORY_WITH_JOURNAL,"largemessages"));

        containerProperties.replace("adminOnly", "false");
        //start in normal mode
        container(1).start(containerProperties);
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
            closeJmsConnection(ctx, conn, session);
        }

        assertNotNull("Received message should not be null", received);
        assertNull("Tested property should be null", received.getStringProperty("test_property"));
        assertTrue("Test message should be received as proper message type", received instanceof TextMessage);
        assertEquals("Test message body should be preserved", "Test text", ((TextMessage) received).getText());
    }

    private void prepareServer(final Container container) {

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
        ops.close();
        container.stop();
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
