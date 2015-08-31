package org.jboss.qa.hornetq.test.failover.jms20;


import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients20.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.clients20.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.LocalCopyMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author mnovak@redhat.com
 * @author msvehla@redhat.com
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-xa-transactions
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional
 * @tpSince EAP6
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class BytemanLodh1TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(BytemanLodh1TestCase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 5; //10000;

    private static final int LARGE_MESSAGE_SIZE = 1048576;

    // queue to send messages in
    private static final String IN_QUEUE_NAME = "InQueue";

    private static final String IN_QUEUE = "jms/queue/" + IN_QUEUE_NAME;

    // queue for receive messages out
    private static final String OUT_QUEUE_NAME = "OutQueue";

    private static final String OUT_QUEUE = "jms/queue/" + OUT_QUEUE_NAME;

    private final Archive mdb1Lodh1 = createLodh1Deployment();
    private final Archive mdb2Copy = createLodh1CopyDeployment();

    public JavaArchive createLodh1Deployment() {
        JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh1");
        mdbJar.addClass(LocalMdbFromQueue.class);

        String ejbXml = createEjbJarXml(LocalMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset(ejbXml), "jboss-ejb3.xml");
        logger.info(ejbXml);

        logger.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public JavaArchive createLodh1CopyDeployment() {
        JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh1-copy");
        mdbJar.addClass(LocalCopyMdbFromQueue.class);

        String ejbXml = createEjbJarXml(LocalCopyMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset(ejbXml), "jboss-ejb3.xml");
        logger.info(ejbXml);

        logger.info(mdbJar.toString(true));
        File target = new File("/tmp/mdb-copy.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill the server before XA transaction start in RA
     * and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server before XA transaction start in RA</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
        @BMRule(name = "server kill on transaction start",
                targetClass = "org.hornetq.ra.HornetQRAXAResource",
                targetMethod = "start",
                action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
        @BMRule(name = "server kill on transaction start",
                targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                targetMethod = "start",
                action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillOnTransactionStart() throws Exception {
        this.generalLodh1Test();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill the server after XA transaction start in RA
     * and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server after XA transaction start in RA</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "server kill after transaction start",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "start",
            targetLocation = "EXIT",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill after transaction start",
                    targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                    targetMethod = "start",
                    targetLocation = "EXIT",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillAfterTransactionStart() throws Exception {
        this.generalLodh1Test();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill the server before XA transaction end in RA
     * and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server before XA transaction end in RA</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "server kill on transaction end",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "end",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill on transaction end",
                    targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                    targetMethod = "end",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillOnTransactionEnd() throws Exception {
        this.generalLodh1Test();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill the server after XA transaction end in RA
     * and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server after XA transaction end in RA</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
    @BMRule(name = "server kill after transaction end",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "end",
            targetLocation = "EXIT",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill after transaction end",
                    targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                    targetMethod = "end",
                    targetLocation = "EXIT",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillAfterTransactionEnd() throws Exception {
        this.generalLodh1Test();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill the server before XA transaction prepare in RA
     * and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server before XA transaction prepare in RA</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
    @BMRule(name = "server kill on transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill on transaction prepare",
                    targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                    targetMethod = "prepare",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillOnTransactionPrepare() throws Exception {
        this.generalLodh1Test();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill the server after XA transaction prepare in RA
     * and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server after XA transaction prepare in RA</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
    @BMRule(name = "server kill after transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            isAfter = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill after transaction prepare",
                    targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                    targetMethod = "prepare",
                    isAfter = true,
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillAfterTransactionPrepare() throws Exception {
        this.generalLodh1Test();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill the server before XA transaction commit in RA
     * and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server before XA transaction commit in RA</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
    @BMRule(name = "server kill on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill on transaction commit",
                    targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                    targetMethod = "commit",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillOnTransactionCommit() throws Exception {
        this.generalLodh1Test();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill the server after XA transaction commit in RA
     * and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server after XA transaction commit in RA</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
    @BMRule(name = "server kill after transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            targetLocation = "EXIT",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill after transaction commit",
                    targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillAfterTransactionCommit() throws Exception {
        this.generalLodh1Test();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send large messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill the server before XA transaction commit in RA
     * and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends large messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server before XA transaction commit in RA</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
    @BMRule(name = "server kill on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill on transaction commit",
                    targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                    targetMethod = "commit",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillWithLargeMessagesOnTransactionCommit() throws Exception {
        this.generalLodh1Test();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send large messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill server when creating large message during XA transaction
     * and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends large messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server when creating large message during XA transaction</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
    @BMRule(name = "server kill on large message create",
            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
            targetMethod = "createLargeMessage",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill on large message create",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "createLargeMessage",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillOnCreatingLargeMessage() throws Exception {
        this.generalLodh1Test(mdb2Copy, new ByteMessageBuilder(LARGE_MESSAGE_SIZE));
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send large messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill server when sending large message during XA transaction
     * and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends large messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server when sending large message during XA transaction</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
//    @BMRule(name = "server kill on large message file send",
//            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
//            targetMethod = "sendLargeMessageFiles",
//            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    @BMRule(name = "server kill on large message file send",
            targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
            targetMethod = "sendContinuations",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill on large message file send",
                    targetClass = "org.apache.activemq.artemis.core.server.impl.ServerSessionImpl",
                    targetMethod = "sendContinuations",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillOnSendingLargeMessage() throws Exception {
        this.generalLodh1Test(mdb2Copy, new ByteMessageBuilder(LARGE_MESSAGE_SIZE));
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send large messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill server when creating a new file for a large message
     * during XA transaction and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends large messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server when creating a new file for a large message during XA transaction</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
    @BMRule(name = "server kill on large message file create",
            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
            targetMethod = "createFileForLargeMessage",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill on large message file create",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "createFileForLargeMessage",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    public void testServerKillOnCreatingLargeMessageFile() throws Exception {
        this.generalLodh1Test(mdb2Copy, new ByteMessageBuilder(LARGE_MESSAGE_SIZE));
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send large messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Kill server when deleting a large message file
     * during XA transaction and restart it. Read messages from OutQueue
     * @tpInfo  For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start producer which sends large messages to InQueue</li>
     *     <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *     <li>kill the server when deleting a large message file during XA transaction</li>
     *     <li>restart the server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
    @BMRule(name = "server kill on large message file delete",
            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
            targetMethod = "deleteLargeMessageFile",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill on large message file delete",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "deleteLargeMessageFile",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    @Ignore
    public void testServerKillOnDeletingLargeMessageFilePassThrough() throws Exception {
        this.generalLodh1Test(mdb2Copy, new ByteMessageBuilder(LARGE_MESSAGE_SIZE));
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
    @BMRule(name = "server kill on large message file delete",
            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
            targetMethod = "deleteLargeMessageFile",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
            @BMRule(name = "server kill on large message file delete",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "deleteLargeMessageFile",
                    action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")})
    @Ignore
    public void testServerKillOnDeletingLargeMessageFile() throws Exception {
        this.generalLodh1Test(mdb1Lodh1, new ByteMessageBuilder(LARGE_MESSAGE_SIZE));
    }


    private void generalLodh1Test() throws Exception {
        this.generalLodh1Test(mdb1Lodh1, new ClientMixMessageBuilder(10, 150));
    }


    private void generalLodh1Test(final Archive deployment, final MessageBuilder msgBuilder) throws Exception {

        prepareJmsServer(container(1));

        container(1).start();

        logger.info("!!!!! FIRST PASS !!!!!");
        logger.info("Sending messages to InQueue");
        this.sendMessages(msgBuilder);

        logger.info("Deploying MDB " + deployment);
        RuleInstaller.installRule(this.getClass(), container(1));
        try {
            container(1).deploy(deployment);
        } catch (Exception e) {
            // byteman might kill the server before control returns back here from deploy method, which results
            // in arquillian exception; it's safe to ignore, everything is deployed and running correctly on the server
            logger.debug("Arquillian got an exception while deploying", e);
        }

        container(1).waitForKill();
        container(1).start();

        // check that number of prepared transaction gets to 0
        logger.info("Get information about transactions from HQ:");
        long timeout = 300000;
        long startTime = System.currentTimeMillis();
        int numberOfPreparedTransaction = 100;
        JMSOperations jmsOperations = container(1).getJmsOperations();
        while (numberOfPreparedTransaction > 0 && System.currentTimeMillis() - startTime < timeout) {
            numberOfPreparedTransaction = jmsOperations.getNumberOfPreparedTransaction();
            Thread.sleep(1000);
        }
        jmsOperations.close();
        // wait for InQueue to be empty
        new JMSTools().waitForMessages(IN_QUEUE_NAME, 0, 300000, container(1));
        // wait for OutQueue to have NUMBER_OF_MESSAGES_PER_PRODUCER
        new JMSTools().waitForMessages(OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1));

        List<java.util.Map<String, String>> receivedMessages = readMessages();

        assertEquals("Incorrect number of received messages", 5, receivedMessages.size());
        assertTrue("Large messages directory should be empty", this.isLargeMessagesDirEmpty());

        container(1).stop();

        Assert.assertEquals("Number of prepared transactions must be 0", 0, numberOfPreparedTransaction);

    }


    private List<String> sendMessages(final MessageBuilder builder) throws Exception {
        SoakProducerClientAck producer = new SoakProducerClientAck(container(1), IN_QUEUE,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        builder.setAddDuplicatedHeader(false);
        producer.setMessageBuilder(builder);

        logger.info("Start producer.");
        producer.start();
        producer.join();

        return producer.getListOfSentMessages();
    }


    private List<java.util.Map<String, String>> readMessages() throws Exception {
        ReceiverTransAck receiver = null;
        logger.info("Start receiver.");

        try {
            receiver = new ReceiverTransAck(container(1), OUT_QUEUE, 5000, 10, 10);
            receiver.start();
            receiver.join();
            return receiver.getListOfReceivedMessages();
        } catch (Exception e) {
            logger.info("Caought exception in client, shutting it down", e);
            if (receiver != null) {
                return receiver.getListOfReceivedMessages();
            } else {
                return Collections.emptyList();
            }
        }
    }


    @Before
    @After
    @Override
    public void stopAllServers() {
        container(1).stop();
    }



    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServer(Container container) {
            prepareJmsServerEAP6EAP7(container);
    }

    private void prepareJmsServerEAP6EAP7(Container container) {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 512 * 1024, 0, 0, 50 * 1024);
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.removeBroadcastGroup("bg-group1");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");
        jmsAdminOperations.setNodeIdentifier(1234567);

        // enable trace logs
        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        jmsAdminOperations.seRootLoggingLevel("TRACE");

        try {
            jmsAdminOperations.removeQueue(IN_QUEUE_NAME);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createQueue("default", IN_QUEUE_NAME, IN_QUEUE, true);

        try {
            jmsAdminOperations.removeQueue(OUT_QUEUE_NAME);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createQueue("default", OUT_QUEUE_NAME, OUT_QUEUE, true);
        jmsAdminOperations.close();
        container.stop();
    }

    private static String createEjbJarXml(final Class<?> mdbClass) {

        StringBuilder ejbXml = new StringBuilder();
        ejbXml.append("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n");
        ejbXml.append("<jboss:ejb-jar xmlns:jboss=\"http://www.jboss.com/xml/ns/javaee\"\n");
        ejbXml.append("xmlns=\"http://java.sun.com/xml/ns/javaee\"\n");
        ejbXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        ejbXml.append("xmlns:c=\"urn:clustering:1.0\"\n");
        ejbXml.append("xsi:schemaLocation=\"http://www.jboss.com/xml/ns/javaee ")
                .append("http://www.jboss.org/j2ee/schema/jboss-ejb3-2_0.xsd http://java.sun.com/xml/ns/javaee ")
                .append("http://java.sun.com/xml/ns/javaee/ejb-jar_3_1.xsd\"\n");
        ejbXml.append("version=\"3.1\"\n");
        ejbXml.append("impl-version=\"2.0\">\n");
        ejbXml.append("<enterprise-beans>\n");
        ejbXml.append("<message-driven>\n");
        ejbXml.append("<ejb-name>mdb-lodh1</ejb-name>\n");
        ejbXml.append("<ejb-class>").append(mdbClass.getName()).append("</ejb-class>\n");
        ejbXml.append("<activation-config>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destination</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>").append(IN_QUEUE)
                .append("</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destinationType</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>javax.jms.Queue</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("</activation-config>\n");
        ejbXml.append("<resource-ref>\n");
        ejbXml.append("<res-ref-name>queue/OutQueue</res-ref-name>\n");
        ejbXml.append("<jndi-name>").append(OUT_QUEUE).append("</jndi-name>\n");
        ejbXml.append("<res-type>javax.jms.Queue</res-type>\n");
        ejbXml.append("<res-auth>Container</res-auth>\n");
        ejbXml.append("</resource-ref>\n");
        ejbXml.append("</message-driven>\n");
        ejbXml.append("</enterprise-beans>\n");
        ejbXml.append("</jboss:ejb-jar>\n");
        ejbXml.append("\n");

        return ejbXml.toString();
    }


    private boolean isLargeMessagesDirEmpty() {
        String path = container(1).getServerHome() + File.separator
                + "standalone" + File.separator
                + "data" + File.separator
                + container(1).getJmsOperations().getJournalLargeMessageDirectoryPath();

        File largeMessagesDir = new File(path);

        if (!largeMessagesDir.isDirectory() || !largeMessagesDir.canRead()) {
            throw new IllegalStateException("Cannot access large messages directory " + path);
        }

        // Deleting the file is async... we keep looking for a period of the time until the file is really gone
        long timeout = System.currentTimeMillis() + 5000;
        while (timeout > System.currentTimeMillis() && largeMessagesDir.listFiles().length != 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }

        if (0 != largeMessagesDir.listFiles().length) {
            for (File file : largeMessagesDir.listFiles()) {
                System.out.println("File " + file + " still on ");
            }
        }

        return largeMessagesDir.listFiles().length == 0;
    }

}