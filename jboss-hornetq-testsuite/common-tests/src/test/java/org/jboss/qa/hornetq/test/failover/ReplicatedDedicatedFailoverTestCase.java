package org.jboss.qa.hornetq.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.journalreplication.utils.JMSUtil;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.jms.Message;
import javax.jms.Session;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF  STANDALONE JMS CLIENT WITH REPLICATED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated-replicated-journal/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated-replicated-journal-win/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 * @tpTestCaseDetails HornetQ journal is located on GFS2 on SAN where journal type ASYNCIO must be used.
 * Or on NSFv4 where journal type is ASYNCIO or NIO.
 */
public class ReplicatedDedicatedFailoverTestCase extends DedicatedFailoverTestCase {

    private static final Logger logger = Logger.getLogger(DedicatedFailoverTestCase.class);

    private String replicationGroupName = "replication-group-name-1";

    @After
    @Before
    public void stopAllServers() {
        container(1).stop();
        container(2).stop();
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed just before message and  transactional data about producer's incoming
     * message are written into journal.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     * <li>Install Byteman rule, which kills server just before transactional data about receiving message are written in to Journal</li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill before transactional data are written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckQueueMessageSentNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, false);
    }


    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed after message is written in to journal, but before transactional data
     * about producer's incoming message are written.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     * <li>Install Byteman rule, which kills server just before transactional data about receiving message are written in to Journal</li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill before transactional data are written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckQueueMessageSentStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, false);
    }


    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when commit is sent to journal and NOT stored.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     * <li>Install Byteman rule, which kills server just before commit is written to journal</li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill before transaction commit is written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckQueueCommitSentNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, false);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when commit is sent to journal and stored.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     * <li>Install Byteman rule, which kills server after commit is written to journal</li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill after transaction commit is written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckQueueCommitSentAndStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, false);
    }


    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when commit is witten in to backup's journal.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     * <li>Install Byteman rule, which kills live server after commit is written to backup's journal</li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckQueueCommitStoredInBackupNotStoredInLive() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, false);
    }


    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when message is received but not acked.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     * <li>Install Byteman rule, which kills live server after message is received but not acked</li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after message is deleted from journal - receive",
                    targetClass = "org.hornetq.core.replication.ReplicatedJournal",
                    targetMethod = "appendDeleteRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill after message is deleted from journal - receive",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicatedJournal",
                    targetMethod = "appendDeleteRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckQueueMessageReceivedNotAcked() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed before commit of received message is stored in journal.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     * <li>Install Byteman rule, which kills live server before commit of received message is stored in journal/li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before commit is stored to journal - receive",
                    targetClass = "org.hornetq.core.replication.ReplicatedJournal",
                    targetMethod = "appendCommitRecord",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill before commit is stored to journal - receive",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicatedJournal",
                    targetMethod = "appendCommitRecord",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")

    })
    public void replicatedTestFailoverTransAckQueueCommitNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when after commit of received message is stored in journal.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     * <li>Install Byteman rule, which kills live after before commit of received message is stored in journal/li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after commit is stored to journal - receive",
                    targetClass = "org.hornetq.core.replication.ReplicatedJournal",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\"); killJVM();"),
            @BMRule(name = "Artemis Kill after commit is stored to journal - receive",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicatedJournal",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\"); killJVM();")
    })
    public void replicatedTestFailoverTransAckQueueCommitStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when after commit of received message is stored in backup's journal.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     * <li>Install Byteman rule, which kills live after before commit of received message is stored in backup's journal/li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckQueueCommitStoredInBackupNotStoredInLiveReceive() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, true);
    }

    /**
     *
     *
     * when message is sent and not stored into journal (P1)
     when message is sent and stored into journal but response is not returned (P1)
     message is replicated to backup
     message is not replicated to backup
     when message is sent and response for commit not recieved (P1)
     commit is not written to journal
     commit is written to live but not to backup
     commit is written to live/backup
     when message is received but not acked/commited (P1)
     when message is received but response for ack not received (P1)
     ack/commit is not written to journal
     ack/commit is written to live but not to backup
     ack/commit is written to live/backup
     *
     */

    /////////////////////////////////////////// FAILOVER ON TOPIC ///////////////////////////////////////////////

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed just before message and  transactional data about producer's incoming
     * message are written into journal.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testTopic on node-1 and receiving them from testTopic on node-1</li>
     * <li>Install Byteman rule, which kills server just before transactional data about receiving message are written in to Journal</li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill before transactional data are written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckTopicMessageSentNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, false);
    }


    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed after message is written in to journal, but before transactional data
     * about producer's incoming message are written.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testTopic on node-1 and receiving them from testTopic on node-1</li>
     * <li>Install Byteman rule, which kills server just before transactional data about receiving message are written in to Journal</li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill before transactional data are written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckTopicMessageSentStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, false);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when commit is sent to journal and NOT stored.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testTopic on node-1 and receiving them from testTopic on node-1</li>
     * <li>Install Byteman rule, which kills server just before commit is written to journal</li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill before transaction commit is written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckTopicCommitSentNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, false);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when commit is sent to journal and stored.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testTopic on node-1 and receiving them from testTopic on node-1</li>
     * <li>Install Byteman rule, which kills server after commit is written to journal</li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill after transaction commit is written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckTopicCommitSentAndStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, false);
    }

    /**
     * Start simple failover test with trans_ack on Topis. Server is killed when commit is sent to journal and stored.
     * It's the same method for client_ack and trans session.
     */
    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when after commit of sent message is stored in backup's journal.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testTopic on node-1 and receiving them from testTopic on node-1</li>
     * <li>Install Byteman rule, which kills live after before commit of sent message is stored in backup's journal/li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckTopicCommitStoredInBackupNotStoredInLive() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, false);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when message is received but not acked.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testTopic on node-1 and receiving them from testTopic on node-1</li>
     * <li>Install Byteman rule, which kills live server after message is received but not acked</li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after message is deleted from journal - receive",
                    targetClass = "org.hornetq.core.replication.ReplicatedJournal",
                    targetMethod = "appendDeleteRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill after message is deleted from journal - receive",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendDeleteRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
    })
    public void replicatedTestFailoverTransAckTopicMessageReceivedNotAcked() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, true);
    }


    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed before commit of received message is stored in journal.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testTopic on node-1 and receiving them from testTopic on node-1</li>
     * <li>Install Byteman rule, which kills live server before commit of received message is stored in journal/li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before commit is stored to journal - receive",
                    targetClass = "org.hornetq.core.replication.ReplicatedJournal",
                    targetMethod = "appendCommitRecord",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill before commit is stored to journal - receive",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicatedJournal",
                    targetMethod = "appendCommitRecord",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")

    })

    public void replicatedTestFailoverTransAckTopicCommitNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when after commit of received message is stored in journal.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testTopic on node-1 and receiving them from testTopic on node-1</li>
     * <li>Install Byteman rule, which kills live after before commit of received message is stored in journal/li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after commit is stored to journal - receive",
                    targetClass = "org.hornetq.core.replication.ReplicatedJournal",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\"); killJVM();"),
            @BMRule(name = "Artemis Kill after commit is stored to journal - receive",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicatedJournal",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\"); killJVM();")
    })
    public void replicatedTestFailoverTransAckTopicCommitStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to server in dedicated topology with
     * replicated journal. Live server is killed when after commit of received message is stored in backup's journal.
     * @tpProcedure <ul>
     * <li>start two nodes in dedicated cluster topology</li>
     * <li>start sending messages to testTopic on node-1 and receiving them from testTopic on node-1</li>
     * <li>Install Byteman rule, which kills live after before commit of received message is stored in backup's journal/li>
     * <li>clients make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Artemis Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    })
    public void replicatedTestFailoverTransAckTopicCommitStoredInBackupNotStoredInLiveReceive() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, true);
    }


    /**
     * @throws Exception
     * @tpTestDetails This test scenario tests if clients are not blocked, when backup fails during synchronization.
     * @tpProcedure <ul>
     * <li>Configure 2 nodes to dedicated topology</li>
     * <li>Start live (node-1) and send 1GB of large-messages</li>
     * <li>Start other producer, sending messages to node-1</li>
     * <li>Start consumer, receiving messages from node-1</li>
     * <li>Start backup (node-2) and wait until synchronization starts</li>
     * <li>Shut down node-2</li>
     * <li>Check if clients still send and receive messages</li>
     * </ul>
     * @tpPassCrit clients are not blocked after backup shutdown
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientsNotBlockedWhenBackupStoppedDuringSynchronization() throws Exception {

        int numberOfMessages = 1000;

        prepareSimpleDedicatedTopology();

        container(1).start();

        // send lots of messages (GBs)
        logger.info("Start producer to send: " + numberOfMessages + " messages.");

        long producerStartTime = System.currentTimeMillis();

        ProducerTransAck prod1 = new ProducerTransAck(container(1), queueJndiNamePrefix + "0", numberOfMessages);

        prod1.setMessageBuilder(new TextMessageBuilder(1024 * 1024)); // 1MB

        prod1.setTimeout(0);

        prod1.setCommitAfter(20);

        prod1.start();

        prod1.join();

        long producerFinishTime = System.currentTimeMillis() - producerStartTime;

        logger.info("Producer sent: " + numberOfMessages + " messages.");


        logger.info("Start producer and consumer.");
        // start one producer and consumer - client ack - those get blocked for 2 min. later when backup is stopped

        ProducerClientAck producer = new ProducerClientAck(container(1), queueJndiNamePrefix + "0", 300);

        MessageBuilder builder = new TextMessageBuilder(1024 * 1024);

        builder.setAddDuplicatedHeader(true);

        producer.setMessageBuilder(builder);

        producer.setTimeout(100);

        producer.start();

        ReceiverClientAck receiver = new ReceiverClientAck(container(1), queueJndiNamePrefix + "0", 30000, 1, 100);

        receiver.setTimeout(100);

        receiver.start();

        // start backup
        logger.info("Start backup server.");

        container(2).start();

        logger.info("Backup started - synchronization with live will started now.");

        // put here some safe time, replication cannot be finished - lets say is a safe value producerFinishTime/2
        Thread.sleep(producerFinishTime / 2);

        // during synchronization live-> backup stop backup (it takes 2 min for live disconnect backup and org.jboss.qa.hornetq.apps.clients continue to work)
        logger.info("Stop backup server - synchronization with live must be in progress now.");

        container(2).stop();

        logger.info("Backup server stopped");

        // now check whether producer and consumer sent/received some messages
        long timeout = 60000;
        // wait for 1 min for producers and consumers to receive more messages
        long startTime = System.currentTimeMillis();

        Thread.sleep(10000);

        int startValueProducer = producer.getListOfSentMessages().size();

        int startValueConsumer = receiver.getListOfReceivedMessages().size();

        logger.info("Check that org.jboss.qa.hornetq.apps.clients did send or received messages in next: " + timeout);

        while (producer.getListOfSentMessages().size() <= startValueProducer && receiver.getListOfReceivedMessages().size() <= startValueConsumer) {

            if (System.currentTimeMillis() - startTime > timeout) {

                Assert.fail("Clients - producer and consumer did not sent/received new messages after backup was stopped for 60 s.");
            }

            try {

                Thread.sleep(1000);

            } catch (InterruptedException e) {
                // ignore
            }
        }

        logger.info("Client did send or received messages in timeout: " + timeout);

        // ok, stop org.jboss.qa.hornetq.apps.clients.
        producer.stopSending();

        receiver.interrupt();

        producer.join();

        receiver.join();

        container(1).stop();
    }

    /**
     * @throws Exception
     * @tpTestDetails This test scenario tests if producer is not blocked or crashed when backup is killed after synchronization.
     * @tpProcedure <ul>
     * <li>Configure 2 nodes in replicated dedicated topology</li>
     * <li>Start live (node-1) and backup (node-2) and start sending messages</li>
     * <li>Start producer</li>
     * <li>Kill node-2</li>
     * <li>Start consumer, receiving messages from node-1</li>
     * <li>Check if clients still send and receive messages</li>
     * </ul>
     * @tpPassCrit clients are not blocked after backup shutdown
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testProducerBackupIsKilledAfterSynchronization() throws Exception {
        testProducerDontCrashWhenBackupIsCrashedAfterSynchronization(Constants.FAILURE_TYPE.KILL, new ClientMixMessageBuilder(10, 200));
    }

    /**
     * @throws Exception
     * @tpTestDetails This test scenario tests if producer is not blocked or crashed when backup is shutdown after synchronization.
     * @tpProcedure <ul>
     * <li>Configure 2 nodes in replicated dedicated topology</li>
     * <li>Start live (node-1) and backup (node-2) and start sending messages</li>
     * <li>Start producer</li>
     * <li>Shut down node-2</li>
     * <li>Start consumer, receiving messages from node-1</li>
     * <li>Check if clients still send and receive messages</li>
     * </ul>
     * @tpPassCrit clients are not blocked after backup shutdown
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testProducerBackupIsShutdownAfterSynchronization() throws Exception {
        testProducerDontCrashWhenBackupIsCrashedAfterSynchronization(Constants.FAILURE_TYPE.SHUTDOWN, new ClientMixMessageBuilder(10, 200));
    }


    public void testProducerDontCrashWhenBackupIsCrashedAfterSynchronization(Constants.FAILURE_TYPE failureType, MessageBuilder messageBuilder) throws Exception {

        int numberOfMessages = 20000;

        prepareSimpleDedicatedTopology();

        container(2).start();
        container(1).start();

        // TODO once https://issues.jboss.org/browse/JBEAP-4136 is resolved then replace thread sleep by proper check
        Thread.sleep(10000);

        logger.info("Start producer to send: " + numberOfMessages + " messages.");

        ProducerTransAck prod1 = new ProducerTransAck(container(1), queueJndiNamePrefix + "0", numberOfMessages);
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        prod1.addMessageVerifier(messageVerifier);
        prod1.setMessageBuilder(messageBuilder);
        prod1.setTimeout(0);
        prod1.setCommitAfter(10);
        prod1.start();

        new JMSTools().waitForMessages(queueNamePrefix + "0", 300, 120000, container(1));

        logger.info("Crash backup server - " + failureType);
        container(2).fail(failureType);
        logger.info("Backup server crashed by - " + failureType);

        waitForClientToFailover(prod1, 120000);

        new JMSTools().waitForMessages(queueNamePrefix + "0", 600, 120000, container(1));

        ReceiverClientAck receiver = new ReceiverClientAck(container(1), queueJndiNamePrefix + "0", 20000, 1, 100);
        receiver.setTimeout(0);
        receiver.addMessageVerifier(messageVerifier);
        receiver.start();

        prod1.stopSending();
        prod1.join();
        receiver.join();

        boolean isOK = messageVerifier.verifyMessages();
        Assert.assertTrue("There were detected losses or duplicates. Check logs for more details.", isOK);

        container(1).stop();
    }

    /**
     * @throws Exception
     * @tpTestDetails This test scenario tests if clients is not blocked or crashed when backup is killed after synchronization.
     * @tpProcedure <ul>
     * <li>Configure 2 nodes in replicated dedicated topology</li>
     * <li>Start live (node-1) and backup (node-2) and start sending messages</li>
     * <li>Start producer and receiver</li>
     * <li>Kill node-2</li>
     * <li>Check if clients still send and receive messages</li>
     * </ul>
     * @tpPassCrit clients are not blocked after backup shutdown
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientsBackupIsKilledAfterSynchronization() throws Exception {
        testClientsDontCrashWhenBackupIsCrashedAfterSynchronization(Constants.FAILURE_TYPE.KILL, new ClientMixMessageBuilder(10, 200));
    }

    /**
     * @throws Exception
     * @tpTestDetails This test scenario tests if clients is not blocked or crashed when backup is shutdown after synchronization.
     * @tpProcedure <ul>
     * <li>Configure 2 nodes in replicated dedicated topology</li>
     * <li>Start live (node-1) and backup (node-2) and start sending messages</li>
     * <li>Start producer and receiver</li>
     * <li>Shut down node-2</li>
     * <li>Start consumer, receiving messages from node-1</li>
     * <li>Check if clients still send and receive messages</li>
     * </ul>
     * @tpPassCrit clients are not blocked after backup shutdown
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientsBackupIsShutdownAfterSynchronization() throws Exception {
        testClientsDontCrashWhenBackupIsCrashedAfterSynchronization(Constants.FAILURE_TYPE.SHUTDOWN, new ClientMixMessageBuilder(10, 200));
    }

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     *
     * @throws Exception
     */
    @BMRules({
            @BMRule(name = "HornetQ: Increment counter after the backup is synced with live",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    condition = "!$0.isSynchronizing()",
                    action = "incrementCounter(\"counter\")"),
            @BMRule(name = "HornetQ: Kill server after the counter is 10",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    condition = "readCounter(\"counter\") == 10",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();"),
            @BMRule(name = "Artemis: Increment counter after the backup is synced with live",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    condition = "!$0.isSynchronizing()",
                    action = "incrementCounter(\"counter\")"),
            @BMRule(name = "Artemis: Kill server after the counter is 10",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    condition = "readCounter(\"counter\") == 10",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();"),
    })
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMultipleFailoverReceiver() throws Exception {

        String testQueue0JndiName = queueJndiNamePrefix + "0";

        int numberOfMessages = 50000;
        MessageBuilder messageBuilder = new TextMessageBuilder(10);
        messageBuilder.setAddDuplicatedHeader(true);

        prepareSimpleDedicatedTopology();

        container(1).start();

        container(2).start();

        Thread.sleep(10000);

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), testQueue0JndiName, numberOfMessages);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        producerToInQueue1.addMessageVerifier(messageVerifier);
        producerToInQueue1.setCommitAfter(100);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.start();
        producerToInQueue1.join();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), testQueue0JndiName, 30000, 5, 10);
        receiver1.setTimeout(5);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();

        long startTime = System.currentTimeMillis();
        while (receiver1.getListOfReceivedMessages().size() < 120 && System.currentTimeMillis() - startTime < 60000) {
            Thread.sleep(1000);
        }

        for (int numberOfFailovers = 0; numberOfFailovers < 10; numberOfFailovers++) {

            logger.warn("########################################");
            logger.warn("Running new cycle for multiple failover - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            logger.warn("########################################");
            logger.warn("Kill live server - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");
            RuleInstaller.installRule(this.getClass(), container(1));

            logger.warn("Wait some time to give chance backup to come alive and receiver to failover");
            Assert.assertTrue("Backup did not start after failover - failover failed -  - number of failovers: "
                    + numberOfFailovers, CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(),
                    container(2).getHornetqPort(), 300000));
            CheckServerAvailableUtils.waitForBrokerToActivate(container(2), 300000);
            container(1).kill();


            if (!receiver1.isAlive()) {
                break;
            }

            waitForClientToFailover(receiver1, 300000);

            Thread.sleep(5000); // give it some time

            logger.warn("########################################");
            logger.warn("failback - Start live server again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");
            container(1).start();

            CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 300000);

            logger.warn("########################################");
            logger.warn("failback - Live started again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            // check that backup is really down
            CheckServerAvailableUtils.waitForBrokerToDeactivate(container(2), 60000);


            if (!receiver1.isAlive()) {
                break;
            }

            waitForClientToFailover(receiver1, 300000);

            Thread.sleep(5000); // give it some time

            logger.warn("########################################");
            logger.warn("Ending cycle for multiple failover - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

        }

        receiver1.join();

        boolean isOk = messageVerifier.verifyMessages();
        Assert.assertTrue("There are failures detected by clients. More information in log - search for \"Lost\" or \"Duplicated\" messages",
                isOk);

        container(1).stop();

        container(2).stop();

    }

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     *
     * @param acknowledge acknowledge type
     * @param topic       whether to test with topics
     * @throws Exception
     */
    @BMRules({
            @BMRule(name = "HornetQ: Kill server after the backup is synced with live",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "!$0.isSynchronizing() && flag(\"synced\")",
                    action = "System.out.println(\"Byteman - Synchronization with backup is done.\");(new java.io.File(\"target/synced\")).createNewFile();"),
            @BMRule(name = "Artemis: Kill server after the backup is synced with live",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "!$0.isSynchronizing() && flag(\"synced\")",
                    action = "System.out.println(\"Byteman - Synchronization with backup is done.\");(new java.io.File(\"target/synced\")).createNewFile();")
    })
    public void testMultipleFailover(int acknowledge, boolean topic, boolean shutdown) throws Exception {

        prepareSimpleDedicatedTopology();

        container(1).start();

        container(2).start();

        Thread.sleep(10000);

        clients = createClients(acknowledge, topic);
        clients.setProducedMessagesCommitAfter(2);
        clients.setReceivedMessagesAckCommitAfter(9);
        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 200, 300000);

        ClientUtils.waitForProducersUntil(clients.getProducers(), 100, 300000);

        for (int numberOfFailovers = 0; numberOfFailovers < 50; numberOfFailovers++) {

            logger.warn("########################################");
            logger.warn("Running new cycle for multiple failover - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            if (!shutdown) {

                logger.warn("########################################");
                logger.warn("Kill live server - number of failovers: " + numberOfFailovers);
                logger.warn("########################################");
                RuleInstaller.installRule(this.getClass(), container(1));
                Assert.assertTrue("Live was not synced with backup in 2 minues", waitUntilFileExists("target/synced", 120000));
                container(1).kill();

            } else {

                logger.warn("########################################");
                logger.warn("Shutdown live server - number of failovers: " + numberOfFailovers);
                logger.warn("########################################");
                RuleInstaller.installRule(this.getClass(), container(1));
                Assert.assertTrue("Live was not synced with backup in 2 minues", waitUntilFileExists("target/synced", 120000));
                System.out.println("@@@@@ CONTAINER IS STOPPING @@@@");
                container(1).stop();
            }

            logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
            CheckServerAvailableUtils.waitForBrokerToActivate(container(2), 300000);

            for (Client c : clients.getConsumers()) {
                Assert.assertTrue("Consumer crashed so crashing the test - this happens when client detects duplicates " +
                        "- check logs for message id of duplicated message", c.isAlive());
            }
            waitForClientsToFailover();

            Thread.sleep(5000); // give it some time

            logger.warn("########################################");
            logger.warn("failback - Start live server again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");
            container(1).start();

            Assert.assertTrue("Live did not start again - failback failed - number of failovers: " + numberOfFailovers, CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 300000));

            logger.warn("########################################");
            logger.warn("failback - Live started again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 600000);

            // check that backup is really down
            CheckServerAvailableUtils.waitForBrokerToDeactivate(container(2), 60000);

            for (Client c : clients.getConsumers()) {
                Assert.assertTrue("Consumer crashed so crashing the test - this happens when client detects duplicates " +
                        "- check logs for message id of duplicated message", c.isAlive());
            }
            waitForClientsToFailover();

            Thread.sleep(5000); // give it some time

            logger.warn("########################################");
            logger.warn("Ending cycle for multiple failover - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

        }

        for (Client c : clients.getConsumers()) {
            Assert.assertTrue("Consumer crashed so crashing the test - this happens when client detects duplicates " +
                    "- check logs for message id of duplicated message", c.isAlive());
        }
        waitForClientsToFailover();

        clients.stopClients();
        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container(1).stop();

        container(2).stop();

    }

    public void testClientsDontCrashWhenBackupIsCrashedAfterSynchronization(Constants.FAILURE_TYPE failureType, MessageBuilder messageBuilder) throws Exception {

        int numberOfMessages = 20000;

        prepareSimpleDedicatedTopology();

        container(2).start();
        container(1).start();

        // TODO once https://issues.jboss.org/browse/JBEAP-4136 is resolved then replace thread sleep by proper check
        Thread.sleep(10000);

        logger.info("Start producer to send: " + numberOfMessages + " messages.");

        ProducerTransAck prod1 = new ProducerTransAck(container(1), queueJndiNamePrefix + "0", numberOfMessages);
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        prod1.addMessageVerifier(messageVerifier);
        prod1.setMessageBuilder(messageBuilder);
        prod1.setTimeout(0);
        prod1.setCommitAfter(10);
        prod1.start();

        ReceiverTransAck receiver = new ReceiverTransAck(container(1), queueJndiNamePrefix + "0", 120000, 10, 100);
        receiver.setTimeout(0);
        receiver.addMessageVerifier(messageVerifier);
        receiver.start();

        ClientUtils.waitForReceiverUntil(receiver, 200, 120000);

        logger.info("Crash backup server - " + failureType);
        container(2).fail(failureType);
        logger.info("Backup server crashed by - " + failureType);

        waitForClientToFailover(receiver, 120000);
        waitForClientToFailover(prod1, 120000);

        ClientUtils.waitForReceiverUntil(receiver, 400, 120000);
        prod1.stopSending();
        prod1.join();
        receiver.setReceiveTimeout(5000);
        receiver.join();

        boolean isOK = messageVerifier.verifyMessages();
        Assert.assertTrue("There were detected losses or duplicates. Check logs for more details.", isOK);

        container(1).stop();
    }

    @BMRules({
            @BMRule(name = "HornetQ create counter",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "<init>",
                    action = "createCounter(\"counter\");"),
            @BMRule(name = "HornetQ increment counter",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "!$0.isSynchronizing()",
                    action = "incrementCounter(\"counter\");"
                            + "System.out.println(\"Called org.hornetq.core.replication.ReplicationManage.appendUpdateRecord  - \" + readCounter(\"counter\"));"),
            @BMRule(name = "HornetQ: Kill server after the backup is synced with live",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "readCounter(\"counter\")>120",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();"),
            @BMRule(name = "Artemis create counter",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "<init>",
                    action = "createCounter(\"counter\");"),
            @BMRule(name = "Artemis increment counter",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "!$0.isSynchronizing()",
                    action = "incrementCounter(\"counter\");"
                            + "System.out.println(\"Called org.apache.activemq.artemis.core.replication.ReplicationManage.appendUpdateRecord  - \" + readCounter(\"counter\"));"),
            @BMRule(name = "Artemis: Kill server after the backup is synced with live",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "readCounter(\"counter\")>120",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    })
    public void testFailover(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {
        testFailoverInternal(acknowledge, failback, topic, shutdown);
    }

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     *
     * @param acknowledge acknowledge type
     * @param failback    whether to test failback
     * @param topic       whether to test with topics
     * @throws Exception
     */
    @BMRules({
            @BMRule(name = "Kill server after the backup is synced with live",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "!$0.isSynchronizing()",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();"),
            @BMRule(name = "Kill server after the backup is synced with live",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "!$0.isSynchronizing()",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    })
    public void testFailoverNoPrepare(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {
        testFailoverNoPrepareInternal(acknowledge, failback, topic, shutdown);
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    @Override
    protected void prepareLiveServerEAP6(Container container, String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingForConnector = "messaging";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        if (Constants.CONNECTOR_TYPE.NETTY_NIO.equals(connectorType)) {
            // add connector with NIO
            jmsAdminOperations.removeRemoteConnector(connectorName);
            Map<String, String> connectorParams = new HashMap<String, String>();
            connectorParams.put("use-nio", "true");
            connectorParams.put("use-nio-global-worker-pool", "true");
            jmsAdminOperations.createRemoteConnector(connectorName, messagingGroupSocketBindingForConnector, connectorParams);

            // add acceptor wtih NIO
            Map<String, String> acceptorParams = new HashMap<String, String>();
            acceptorParams.put("use-nio", "true");
            jmsAdminOperations.removeRemoteAcceptor(connectorName);
            jmsAdminOperations.createRemoteAcceptor(connectorName, messagingGroupSocketBindingForConnector, acceptorParams);

        }

        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(false);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.setBackupGroupName("firstPair");
        jmsAdminOperations.setCheckForLiveServer(true);

        jmsAdminOperations.setMaxSavedReplicatedJournals(60);

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

        jmsAdminOperations.setSecurityEnabled(true);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);
//        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");

        jmsAdminOperations.setClusterUserPassword("heslo");
        jmsAdminOperations.removeAddressSettings("#");

        setAddressSettings(jmsAdminOperations);


        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }

        jmsAdminOperations.createQueue(divertedQueue, divertedQueueJndiName, true);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container     The container - defined in arquillian.xml
     * @param journalType   ASYNCIO, NIO
     * @param connectorType whether to use NIO in connectors for CF or old blocking IO, or http connector
     */
    protected void prepareLiveServerEAP7(Container container, String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        setConnectorForClientEAP7(container, connectorType);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyReplicationMaster(true, clusterConnectionName, replicationGroupName);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }
        jmsAdminOperations.createQueue(divertedQueue, divertedQueueJndiName, true);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareBackupServerEAP6(Container container, String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";
        String messagingGroupSocketBindingForConnector = "messaging";
        String pooledConnectionFactoryName = "hornetq-ra";


        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        if (Constants.CONNECTOR_TYPE.NETTY_NIO.equals(connectorType)) {
            // add connector with NIO
            jmsAdminOperations.removeRemoteConnector(connectorName);
            Map<String, String> connectorParams = new HashMap<String, String>();
            connectorParams.put("use-nio", "true");
            connectorParams.put("use-nio-global-worker-pool", "true");
            jmsAdminOperations.createRemoteConnector(connectorName, messagingGroupSocketBindingForConnector, connectorParams);

            // add acceptor wtih NIO
            Map<String, String> acceptorParams = new HashMap<String, String>();
            acceptorParams.put("use-nio", "true");
            jmsAdminOperations.removeRemoteAcceptor(connectorName);
            jmsAdminOperations.createRemoteAcceptor(connectorName, messagingGroupSocketBindingForConnector, acceptorParams);

        }


        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setBackupGroupName("firstPair");
        jmsAdminOperations.setCheckForLiveServer(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(false);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setJournalType(journalType);

        jmsAdminOperations.setMaxSavedReplicatedJournals(60);

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

        jmsAdminOperations.removePooledConnectionFactory(pooledConnectionFactoryName);

        jmsAdminOperations.setSecurityEnabled(true);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.setClusterUserPassword("heslo");

//        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");

        jmsAdminOperations.removeAddressSettings("#");

        setAddressSettings(jmsAdminOperations);

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }

        jmsAdminOperations.createQueue(divertedQueue, divertedQueueJndiName, true);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container     The container - defined in arquillian.xml
     * @param journalType   ASYNCIO, NIO
     * @param connectorType whether to use NIO in connectors for CF or old blocking IO, or HTTP connector
     */
    protected void prepareBackupServerEAP7(Container container, String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        setConnectorForClientEAP7(container, connectorType);

        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyReplicationSlave(true, clusterConnectionName, 5000, replicationGroupName, 60, true, false, null, null, null, null);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }
        jmsAdminOperations.createQueue(divertedQueue, divertedQueueJndiName, true);

        jmsAdminOperations.close();

        container.stop();
    }

    protected void setAddressSettings(JMSOperations jmsAdminOperations) {
        setAddressSettings("default", jmsAdminOperations);
    }

    protected void setAddressSettings(String serverName, JMSOperations jmsAdminOperations) {
        jmsAdminOperations.addAddressSettings(serverName, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
    }

    protected boolean waitUntilFileExists(String path, long timeout) throws InterruptedException {
        File file = new File(path);
        long timeToWait = System.currentTimeMillis() + timeout;
        while (!file.exists() && System.currentTimeMillis() < timeToWait) {
            Thread.sleep(100);
        }
        if (file.exists()) {
            file.delete();
            return true;
        } else {
            return false;
        }
    }

}
