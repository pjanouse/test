package org.jboss.hornetq.test.faultinjection;

import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.jboss.arquillian.container.test.api.*;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.byteman.annotation.BMRule;
import org.jboss.byteman.annotation.BMRules;
import org.jboss.byteman.rule.RuleInstaller;
import org.jboss.hornetq.apps.clients.ProducerClientAckNonHA;
import org.jboss.hornetq.apps.clients.ReceiverClientAckNonHa;
import org.junit.After;
import org.junit.Before;

@RunWith(Arquillian.class)
public class FaultInjectionTestCase {

    public static final String CONTAINER1 = "clustering-udp-0-unmanaged";
    
    public static final String CONTAINER2 = "clustering-udp-1-unmanaged";
   
    @ArquillianResource
    ContainerController controller;
   
    String hostname = "localhost";
    
    String queueName = "testQueue";
   
    /**
     * 
     * Start server "CONTAINER1". Deploy byteman rule in annotations. Run jms
     * producer which kills server - controller.kill() just wait for byteman to
     * kill server. Start server "CONTAINER1" again. Send some messages.
     *
     */
    @Test
    @RunAsClient
    @BMRules(
    @BMRule(name = "rule - kill server after send message", targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl", targetMethod = "processRoute",
    action = "System.out.println(\"Byteman will invoke kill\"); killJVM();"))
    public void simpleFaultInjectionTest() throws InterruptedException  {

        controller.start(CONTAINER1);

        // this will install byteman rule
        RuleInstaller.installRule(this.getClass());

        // run producer asynchronously
        Thread producer = new ProducerClientAckNonHA(hostname, queueName);
        // start producer which will lead to kill server by byteman
        producer.start();

        // this will wait for kill 
        controller.kill(CONTAINER1);

        // start server again 
        controller.start(CONTAINER1);

        //and produce messages
        Thread producer2 = new ProducerClientAckNonHA(hostname, queueName);

        producer2.start();
        
        // consumer messages
        Thread consumer = new ReceiverClientAckNonHa(queueName);
        
        consumer.start();

        // wait for producer2
        producer2.join(40000);
        // wait for consumer
        consumer.join(60000);
        
        controller.stop(CONTAINER1);
        
        controller.stop(CONTAINER2);

    }
    
    @Before @After
    public void stopAllServers()    {
        
        controller.stop(CONTAINER1);
        
        controller.stop(CONTAINER2);
        
    }
    
}
