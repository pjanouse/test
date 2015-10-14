package org.jboss.qa.artemis.test.failover.ntt.standaloneHA;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.apps.servlets.ServletConsumerTransAck;
import org.jboss.qa.hornetq.apps.servlets.ServletProducerTransAck;
import org.jboss.qa.artemis.test.failover.ntt.standalone.NTTStandaloneServerFailureCommitTestCase;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.junit.Test;

/**
 * Created by okalman on 9/15/15.
 */
public class NTTStandaloneHAServerFailureCommitTestCase extends NTTStandaloneServerFailureCommitTestCase {
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
        return true;
    }



    @Override
    @BMRules(
            @BMRule(name = "Kill JMS server before delivers message",
                    targetClass = "org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl",
                    targetMethod = "send",
                    binding = "mypacket:Packet = $packet; ptype:byte = mypacket.getType();",
                    condition = "ptype == 75",
                    targetLocation = "INVOKE Connection.write()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void beforeServerDeliversMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(1,0,-1,false,false,false, true);
    }

    @Override
    @BMRules(
            @BMRule(name = "Kill JMS server after delivers message",
                    targetClass = "org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl",
                    targetMethod = "send",
                    binding = "mypacket:Packet = $packet; ptype:byte = mypacket.getType();",
                    condition = "ptype == 75 ",
                    targetLocation = "INVOKE Connection.write()",
                    isAfter = true,
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void afterServerDeliversMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(1, 0, -1, false, false, false, true);
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
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")})
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void beforeWriteCommitServerDeliversMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(1, 0, 1, false, false, false, true);
    }

    @Override
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
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")})
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void betweenWriteCommitAndDeleteRecordServerDeliversMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(1, 0, 0, false, false, false, true);
    }


}
