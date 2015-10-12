package org.jboss.qa.artemis.test.failover.ntt.standalone;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.servlets.ServletConsumerTransAck;
import org.jboss.qa.hornetq.apps.servlets.ServletProducerTransAck;
import org.jboss.qa.artemis.test.failover.ntt.NTTSeverFailureAbstractTestCase;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Created by okalman on 9/3/15.
 */

@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class NTTStandaloneServerFailureRollbackTestCase extends NTTSeverFailureAbstractTestCase {


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
    // KILL PRODUCER

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer after rollback data are sent, don't wait for ack",
                    targetClass = "org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl",
                    targetMethod = "sendBlocking",
                    isAfter = true,
                    binding = "mypacket:Packet = $packet; ptype:byte = mypacket.getType();",
                    condition = "ptype == 44", //44 is ROLLBACK
                    targetLocation = "INVOKE Connection.write()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void whenProducerRollbackTest() throws Exception {
        overrideMaxMessagesForTest(3);
        producerFailureTestSequence(2, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer after commit data are sent, wait for ack",
                    targetClass = "org.jboss.qa.hornetq.apps.servlets.ServletProducerTransAck",
                    targetMethod = "rollbackAndCommitSession",
                    isAfter = true,
                    targetLocation = "INVOKE Session.rollback()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void afterProducerRollbackTest() throws Exception {
        overrideMaxMessagesForTest(3);
        producerFailureTestSequence(2, true);
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill receiver after rollback data are sent, don't wait for ack",
                    targetClass = "org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl",
                    targetMethod = "sendBlocking",
                    isAfter = true,
                    binding = "mypacket:Packet = $packet; ptype:byte = mypacket.getType();",
                    condition = "ptype == 44", //44 is ROLLBACK
                    targetLocation = "INVOKE Connection.write()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void whenConsumerRollBackTest() throws Exception {
        overrideMaxMessagesForTest(3);
        consumerFailureTestSequence(1, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill receiver after rollback data are sent, wait for ack",
                    targetClass = "org.jboss.qa.hornetq.apps.servlets.ServletConsumerTransAck",
                    targetMethod = "receiveMessages",
                    isAfter = true,
                    targetLocation = "INVOKE Session.rollback()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void afterConsumerRollBackTest() throws Exception {
        overrideMaxMessagesForTest(3);
        consumerFailureTestSequence(1, true);
    }










}
