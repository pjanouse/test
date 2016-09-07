package org.jboss.qa.hornetq.test.failover;


import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.handlers.ifelse.IfHandler;
import org.jboss.logging.Logger;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Simple failover/failback test case which uses only standard JMS API.
 * This test case should simplify investigation of failover/failback issues.
 */
@RunWith(Arquillian.class)
@Prepare("SharedStoreHA")
public class SimpleFailoverTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(SimpleFailoverTestCase.class);

    public static final String QUEUE_NAME = "testQueue";

    public static final String QUEUE_JNDI = "jms/queue/" + QUEUE_NAME;

    public static final String TOPIC_NAME = "testTopic";

    public static final String TOPIC_JNDI = "jms/topic/" + TOPIC_NAME;

    public static long LARGE_MSG_SIZE = 1024 * 1024;

    public static final String clusterConnectionName = "my-cluster";

    @After
    public void stopServers() {
        container(1).stop();
        container(2).stop();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void clientAckQueueFailoverTest() throws Exception {
        failoverTest(Session.CLIENT_ACKNOWLEDGE, false, 10, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void transAckQueueFailoverTest() throws Exception {
        failoverTest(Session.SESSION_TRANSACTED, false, 10, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void clientAckQueueFailbackTest() throws Exception {
        failoverTest(Session.CLIENT_ACKNOWLEDGE, false, 10, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void transAckQueueFailbackTest() throws Exception {
        failoverTest(Session.SESSION_TRANSACTED, false, 10, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void clientAckTopicFailoverTest() throws Exception {
        failoverTest(Session.CLIENT_ACKNOWLEDGE, true, 10, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void transAckTopicFailoverTest() throws Exception {
        failoverTest(Session.SESSION_TRANSACTED, true, 10, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void clientAckTopicFailbackTest() throws Exception {
        failoverTest(Session.CLIENT_ACKNOWLEDGE, true, 10, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void transAckTopicFailbackTest() throws Exception {
        failoverTest(Session.SESSION_TRANSACTED, true, 10, true);
    }

    /**
     * This test simulate simple failover/failback scenario in which live server crash before
     * transaction is committed. After failover client tries to receive the whole bunch of messages again.
     *
     * @param ackMode       acknowledge mode, valid values: {CLIENT_ACKNOWLEDGE, SESSION_TRANSACTED}
     * @param topic         if true the topic is used, otherwise the queue is used
     * @param numMsg        number of messages to be sent and received
     * @param failback      if true the live server is started again after the crash
     * @throws Exception
     */
    @BMRules({
            @BMRule(name = "Hornetq Kill before transaction commit is written into journal - receive",
                    targetClass = "org.hornetq.core.transaction.impl.TransactionImpl",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();"),
            @BMRule(name = "Artemis Kill before transaction commit is written into journal - receive",
                    targetClass = "org.apache.activemq.artemis.core.transaction.impl.TransactionImpl",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    })
    public void failoverTest(int ackMode, boolean topic, int numMsg, boolean failback) throws Exception {
        container(1).start();
        container(2).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");
        Connection connection = cf.createConnection();
        connection.setClientID("client-0");
        Session session;
        Destination destination;

        if (ackMode == Session.CLIENT_ACKNOWLEDGE) {
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        } else if (ackMode == Session.AUTO_ACKNOWLEDGE) {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        } else {
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
        }

        if (topic) {
            destination = (Destination) context.lookup(TOPIC_JNDI);
        } else {
            destination = (Destination) context.lookup(QUEUE_JNDI);
        }


        MessageProducer producer = session.createProducer(destination);
        MessageConsumer consumer;

        if (topic) {
            consumer = session.createDurableSubscriber((Topic) destination, "subscriber-1");
        } else {
            consumer = session.createConsumer(destination);
        }

        connection.start();

        for (int i = 0; i < numMsg; i++) {
            BytesMessage message = session.createBytesMessage();
            for (int j = 0; j < LARGE_MSG_SIZE; j++) {
                message.writeByte(getSampleByte(j));
            }
            message.setStringProperty("id", "message-" + i);
            producer.send(message);
        }
        if (ackMode == Session.SESSION_TRANSACTED) {
            session.commit();
        }

        RuleInstaller.installRule(this.getClass(), container(1));

        Message message = null;

        log.info("Start receive messages");
        for (int i = 0; i < numMsg; i++) {
            message = consumer.receive(5000);
            Assert.assertNotNull(message);
            log.info("Received message " + message.getStringProperty("id"));
            Assert.assertEquals("message-" + i, message.getStringProperty("id"));
        }


        int expectedErrors = 0;
        try {
            if (ackMode == Session.CLIENT_ACKNOWLEDGE) {
                message.acknowledge();
            } else if (ackMode == Session.SESSION_TRANSACTED) {
                session.commit();
            }
        } catch (JMSException e) {
            expectedErrors++;
        }

        Assert.assertEquals(1, expectedErrors);

        if (failback) {
            log.info("FAILBACK: Start live server again.");
            container(1).start();
        }

        int numReceivedMsg;
        int duplicationsDetected;
        while (true) {
            try {
                Set<String> duplicationCache = new HashSet<String>();
                numReceivedMsg = 0;
                duplicationsDetected = 0;
                log.info("Start receive messages");
                for (int i = 0; i < numMsg; ) {
                    Message msg = consumer.receive(60000);
                    if (msg == null) {
                        break;
                    } else {
                        message = msg;
                        numReceivedMsg++;
                    }
                    System.out.println("Received message " + message.getStringProperty("id"));

                    if (duplicationCache.contains(message.getStringProperty("id"))) {
                        duplicationsDetected++;
                        log.warn("Duplication detected " + message.getStringProperty("id"));
                    } else {
                        duplicationCache.add(message.getStringProperty("id"));
                        i++;
                    }
                }

                if (ackMode == Session.CLIENT_ACKNOWLEDGE) {
                    log.info("CALLING message acknowledge");
                    message.acknowledge();
                } else if (ackMode == Session.SESSION_TRANSACTED) {
                    log.info("CALLING session commit");
                    session.commit();
                }
                break;
            }
            catch (JMSException e) {
                log.warn("JMS EXCEPTION CATCHED", e);
                // retry
            }
        }

        Message nullMessage = consumer.receive(5000);

        connection.close();

        log.info("Received messages: " + numReceivedMsg);
        log.info("Duplicated messages: " + duplicationsDetected);
        Assert.assertEquals(numMsg, numReceivedMsg);
        Assert.assertEquals(0, duplicationsDetected);
        Assert.assertNull(nullMessage);
    }

    private byte getSampleByte(long i) {
        return (byte) (i % 256);
    }

}
