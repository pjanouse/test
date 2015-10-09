package org.jboss.qa.hornetq.test.failover.ntt.standalone;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.servlets.ServletConsumerAutoAck;
import org.jboss.qa.hornetq.apps.servlets.ServletProducerAutoAck;
import org.jboss.qa.hornetq.test.failover.ntt.NTTSeverFailureAbstractTestCase;
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
public class NTTStandaloneServerFailureAutoAckTestCase extends NTTSeverFailureAbstractTestCase {


    @Override
    public Class getProducerClass() {
        return ServletProducerAutoAck.class;
    }

    @Override
    public Class getConsumerClass() {
        return ServletConsumerAutoAck.class;
    }
    @Override
    public boolean isClusteredTest(){
        return false;
    }

    @Override
    public boolean isHATest(){
        return false;
    }



    // KILL Producer
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer before send any message",
                    targetClass = "org.jboss.qa.hornetq.apps.servlets.ServletProducerAutoAck",
                    targetMethod = "sendMessage",
                    targetLocation = "INVOKE MessageProducer.send()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void beforeProducerSendTest() throws Exception {
        producerFailureTestSequence(0, true);
    }
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill producer after data are sent,don't wait for ack",
                    targetClass = "org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl",
                    targetMethod = "sendBlocking",
                    isAfter = true,
                    binding = "mypacket:Packet = $packet; ptype:byte = mypacket.getType();",
                    condition = "ptype == 71", //71 is send packet
                    targetLocation = "INVOKE Connection.write()",
                    action = "Thread.sleep(500);System.out.println(\"Byteman will invoke kill\");killJVM();")) //wait a little for server to process
    public void afterProducerSendTest() throws Exception {
        overrideMaxMessagesForTest(1);
        producerFailureTestSequence(1, true);
    }

    //KILL JMS Server

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill server before writing record to the journal",
                    targetClass = "org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    isAfter = false,
                    targetLocation = "INVOKE StorageManager.storeMessage()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void beforeWritingRecordToTheJournalSendTest() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(0,0,0,false,false,false, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill server after writing record to the journal",
                    targetClass = "org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    isAfter = true,
                    targetLocation = "INVOKE StorageManager.storeReference()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void afterWritingRecordToTheJournalSendTest() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(0,MAX_MESSAGES,-1,false,false,false, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill JMS server before delivers message",
                    targetClass = "org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl",
                    targetMethod = "send",
                    binding = "mypacket:Packet = $packet; ptype:byte = mypacket.getType();",
                    condition = "ptype == 75",
                    targetLocation = "INVOKE Connection.write()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void beforeDeliveringMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(0, MAX_MESSAGES, -1, false, false, false, true);
    }
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill JMS server before delivers message",
                    targetClass = "org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl",
                    targetMethod = "send",
                    binding = "mypacket:Packet = $packet; ptype:byte = mypacket.getType();",
                    condition = "ptype == 75",
                    isAfter = true,
                    targetLocation = "INVOKE Connection.write()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void afterDeliveringMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(0, MAX_MESSAGES, -1, false, false, false, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill JMS server after storing ACK to the journal",
                    targetClass = "org.apache.activemq.artemis.core.server.impl.QueueImpl",
                    targetMethod = "acknowledge",
                    isAfter = true,
                    targetLocation = "INVOKE StorageManager.storeAcknowledgeTransactional()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void beforeStoringAckAfterDeliveringMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(0, 1, 1, false, false,false, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill JMS server after storing ACK to the journal",
                    targetClass = "org.apache.activemq.artemis.core.server.impl.QueueImpl",
                    targetMethod = "acknowledge",
                    isAfter = true,
                    targetLocation = "INVOKE StorageManager.storeAcknowledgeTransactional()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void afterStoringAckAfterDeliveringMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(0, 1, 1, false, false, false, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill JMS server after delete record is written to the journal",
                    targetClass = "org.apache.activemq.artemis.core.server.impl.QueueImpl",
                    targetMethod = "postAcknowledge",
                    isAfter = true,
                    targetLocation = "INVOKE StorageManager.deleteMessage()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void afterWriteDeleteRecordDeliveringMessage() throws Exception {
        overrideMaxMessagesForTest(1);
        serverFailureTestSequence(0, 0, 0, false, false, false, true);
    }

    // KILL CONSUMER
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill consumer before sending ack before send any message",
                    targetClass = "org.apache.activemq.artemis.core.client.impl.ClientSessionImpl",
                    targetMethod = "acknowledge",
                    targetLocation = "INVOKE SessionContext.sendACK()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void beforeConsumerAckTest() throws Exception {
        overrideMaxMessagesForTest(1);
        consumerFailureTestSequence(MAX_MESSAGES, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill consumer after sending ack before send any message",
                    targetClass = "org.apache.activemq.artemis.core.client.impl.ClientSessionImpl",
                    targetMethod = "acknowledge",
                    isAfter = true,
                    targetLocation = "INVOKE SessionContext.sendACK()",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void afterConsumerAckTest() throws Exception {
        overrideMaxMessagesForTest(1);
        consumerFailureTestSequence(0, true);
    }





}
