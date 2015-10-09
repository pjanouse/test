package org.jboss.qa.hornetq.test.failover.ntt.standalone;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.apps.servlets.ServletConsumerTransAck;
import org.jboss.qa.hornetq.apps.servlets.ServletProducerTransAck;
import org.jboss.qa.hornetq.test.failover.ntt.NTTNetworkFailureAbstractTestCase;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.junit.Test;

/**
 * Created by okalman on 10/9/15.
 */
public class NTTStandaloneServerNetworkFailureCommitTestCase extends NTTNetworkFailureAbstractTestCase {
    @Override
    public Class getProducerClass() {
        return ServletProducerTransAck.class;
    }

    @Override
    public Class getConsumerClass() {
        return ServletConsumerTransAck.class;
    }
    @Override
    public boolean isClusteredTest(){
        return false;
    }

    @Override
    public boolean isHATest(){
        return false;
    }


    //KILL PRODUCER

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer before send any message",
                    targetClass = "org.jboss.qa.hornetq.apps.servlets.ServletProducerTransAck",
                    targetMethod = "sendMessage",
                    targetLocation = "INVOKE MessageProducer.send()",
                    action = "System.out.println(\"Byteman will invoke network failure\");Runtime.getRuntime().exec(\"sudo /usr/local/bin/network-fail-test fail\");"))
    public void beforeProducerSendTest() throws Exception {
        producerFailureTestSequence(0, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer before commit",
                    targetClass = "org.jboss.qa.hornetq.apps.servlets.ServletProducerTransAck",
                    targetMethod = "commitSession",
                    targetLocation = "INVOKE Session.commit()",
                    action = "System.out.println(\"Byteman will invoke network failure\");Runtime.getRuntime().exec(\"sudo /usr/local/bin/network-fail-test fail\");"))
    public void beforeProducerCommitTest() throws Exception {
        producerFailureTestSequence(0, true);
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer after commit data are sent,don't wait for ack",
                    targetClass = "org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl",
                    targetMethod = "sendBlocking",
                    isAfter = true,
                    binding = "mypacket:Packet = $packet; ptype:byte = mypacket.getType();",
                    condition = "ptype == 43", //43 is COMMIT
                    targetLocation = "INVOKE Connection.write()",
                    action = "System.out.println(\"Byteman will invoke network failure\");Runtime.getRuntime().exec(\"sudo /usr/local/bin/network-fail-test fail\");"))
    public void whenProducerCommitTest() throws Exception {
        overrideMaxMessagesForTest(1);
        producerFailureTestSequence(1, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer before commit",
                    targetClass = "org.jboss.qa.hornetq.apps.servlets.ServletProducerTransAck",
                    targetMethod = "commitSession",
                    isAfter = true,
                    targetLocation = "INVOKE Session.commit()",
                    action = "System.out.println(\"Byteman will invoke network failure\");Runtime.getRuntime().exec(\"sudo /usr/local/bin/network-fail-test fail\");"))
    public void afterProducerCommitTest() throws Exception {
        producerFailureTestSequence(1, true);
    }

    //KILL JMS Server

    @BMRules(
            @BMRule(name = "Kill JMS server before delivers message",
                    targetClass = "org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl",
                    targetMethod = "send",
                    binding = "mypacket:Packet = $packet; ptype:byte = mypacket.getType();",
                    condition = "ptype == 75",
                    targetLocation = "INVOKE Connection.write()",
                    action = "System.out.println(\"Byteman will invoke network failure\");Runtime.getRuntime().exec(\"sudo /usr/local/bin/network-fail-test fail\");"))
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void beforeServerDeliversMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(0,1,-1,false,false,false, true);
    }

    @BMRules(
            @BMRule(name = "Kill JMS server after delivers message",
                    targetClass = "org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl",
                    targetMethod = "send",
                    binding = "mypacket:Packet = $packet; ptype:byte = mypacket.getType();",
                    condition = "ptype == 75 ",
                    targetLocation = "INVOKE Connection.write()",
                    isAfter = true,
                    action = "System.out.println(\"Byteman will invoke network failure\");Runtime.getRuntime().exec(\"sudo /usr/local/bin/network-fail-test fail\");"))
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void afterServerDeliversMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(0, 1, -1, false, false, false, true);
    }

    @BMRules({
            @BMRule(name = "Setup counter for ChannelImpl",
                    targetClass = "org.apache.activemq.artemis.core.transaction.impl.TransactionImpl",
                    targetMethod = "doCommit",
                    action = "createCounter(\"counter\")"),
            @BMRule(name = "Increment counter for every packet containing part of message for consumer",
                    targetClass = "org.apache.activemq.artemis.core.transaction.impl.TransactionImpl",
                    targetMethod = "doCommit",
                    targetLocation = "INVOKE StorageManager.commit()",
                    action = "System.out.println(\"incrementing counter\");incrementCounter(\"counter\");"),
            @BMRule(name = "Kill JMS server after between writing commit and delete record message",
                    targetClass = "org.apache.activemq.artemis.core.transaction.impl.TransactionImpl",
                    targetMethod = "doCommit",
                    condition = "readCounter(\"counter\")==2", // we want second commit, first is from producer
                    targetLocation = "INVOKE StorageManager.commit()",
                    isAfter = true,
                    action = "System.out.println(\"Byteman will invoke network failure\");Runtime.getRuntime().exec(\"sudo /usr/local/bin/network-fail-test fail\");")})
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void betweenWriteCommitAndDeleteRecordServerDeliversMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(0, 0, 0, false, false, false, true);
    }

    // KILL CONSUMER
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer before send any message",
                    targetClass = "org.jboss.qa.hornetq.apps.servlets.ServletConsumerTransAck",
                    targetMethod = "receiveMessage",
                    targetLocation = "INVOKE MessageConsumer.receive()",
                    action = "System.out.println(\"Byteman will invoke network failure\");Runtime.getRuntime().exec(\"sudo /usr/local/bin/network-fail-test fail\");"))
    public void beforeConsumerReceiveTest() throws Exception {
        overrideMaxMessagesForTest(1);
        consumerFailureTestSequence(MAX_MESSAGES, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer before send any message",
                    targetClass = "org.jboss.qa.hornetq.apps.servlets.ServletConsumerTransAck",
                    targetMethod = "commitSession",
                    targetLocation = "INVOKE Session.commit()",
                    action = "System.out.println(\"Byteman will invoke network failure\");Runtime.getRuntime().exec(\"sudo /usr/local/bin/network-fail-test fail\");"))
    public void beforeConsumerCommitTest() throws Exception {

        consumerFailureTestSequence(MAX_MESSAGES, true);
    }
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer after commit data are sent, wait for ack",
                    targetClass = "org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl",
                    targetMethod = "sendBlocking",
                    isAfter = true,
                    binding = "mypacket:Packet = $packet; ptype:byte = mypacket.getType();",
                    condition = "ptype == 43", //43 is COMMIT
                    targetLocation = "INVOKE Connection.write()",
                    action = "System.out.println(\"Byteman will invoke network failure\");Runtime.getRuntime().exec(\"sudo /usr/local/bin/network-fail-test fail\");"))
    public void whenConsumerCommitTest() throws Exception {
        overrideMaxMessagesForTest(1);
        consumerFailureTestSequence(0, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer before send any message",
                    targetClass = "org.jboss.qa.hornetq.apps.servlets.ServletConsumerTransAck",
                    targetMethod = "commitSession",
                    targetLocation = "INVOKE Session.commit()",
                    isAfter = true,
                    action = "System.out.println(\"Byteman will invoke network failure\");Runtime.getRuntime().exec(\"sudo /usr/local/bin/network-fail-test fail\");"))
    public void afterConsumerCommitTest() throws Exception {
        overrideMaxMessagesForTest(2);
        consumerFailureTestSequence(1, true);
    }
}
