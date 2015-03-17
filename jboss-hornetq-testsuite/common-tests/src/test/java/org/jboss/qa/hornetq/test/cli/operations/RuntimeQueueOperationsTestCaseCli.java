package org.jboss.qa.hornetq.test.cli.operations;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.impl.DelayedTextMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;

/**
 * Created by okalman on 2/9/15.
 */

@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class RuntimeQueueOperationsTestCaseCli extends CliTestBase {

    String queueName = "testQueue";
    String queueJndiName = "jms/queue/" + queueName;

    private final String ADDRESS = "/subsystem=messaging/hornetq-server=default/runtime-queue=jms.queue." + queueName;
    private final CliClient cli = new CliClient(new CliConfiguration(getHostname(CONTAINER1), MANAGEMENT_PORT_EAP6, getUsername(CONTAINER1), getPassword(CONTAINER1)));


    @Before
    public void startServerBeforeTest() {
        controller.start(CONTAINER1);
    }

    @After
    public void stopServerAfterTest() {
        stopServer(CONTAINER1);
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void listDeliveringMessagesTestCase() throws Exception{
        Context context = null;
        ConnectionFactory cf = null;
        Connection conn = null;
        Session session = null;
        Queue queue = null;



        int numberOfMessages= 100;
        int commitAfter=50;
        controller.start(CONTAINER1);
        JMSOperations ops = getJMSOperations(CONTAINER1);
        ops.createQueue(queueName, queueJndiName);
        ops.close();
        stopServer(CONTAINER1);
        controller.start(CONTAINER1);
        ProducerAutoAck producer = new ProducerAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),queueJndiName,numberOfMessages);
        producer.start();
        producer.join();



        try {
            context = getContext(getHostname(CONTAINER1), getJNDIPort(CONTAINER1));

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryName());

            conn = cf.createConnection();

            conn.start();

            queue = (Queue) context.lookup(queueJndiName);

            session = conn.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer receiverTransAck = session.createConsumer(queue);

            int counter=0;
            int listDeliveringMessagesSize=-1;
            while(receiverTransAck.receive(500)!=null){
                counter++;
                if(counter%commitAfter==0){
                    session.commit();
                }
                if(counter == commitAfter){
                    listDeliveringMessagesSize=this.getListDeliveringMessagesSize(queueName);
                }
            }
            receiverTransAck.close();;
            Assert.assertEquals("Number of delivering messages does not match", commitAfter, listDeliveringMessagesSize);
        }catch (Exception e){
            e.printStackTrace();
            Assert.assertTrue("Exception was caught", false);


        }finally{
            session.commit();
            session.close();
            conn.close();
        }

        stopServer(CONTAINER1);

    }



    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void listScheduledMessagesTestCase() throws Exception{
        int numberOfMessages= 10;

        controller.start(CONTAINER1);
        JMSOperations ops = getJMSOperations(CONTAINER1);
        ops.createQueue(queueName, queueJndiName);
        ops.close();
        stopServer(CONTAINER1);
        controller.start(CONTAINER1);
        ProducerAutoAck producer = new ProducerAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),queueJndiName,numberOfMessages);
        DelayedTextMessageBuilder delayedTextMessageBuilder= new DelayedTextMessageBuilder(512, 100000);
        producer.setMessageBuilder(delayedTextMessageBuilder);
        producer.start();
        producer.join();
        try{
            Assert.assertEquals("Number of delivering messages does not match", numberOfMessages, getListScheduledMessagesSize(queueName));
        }catch(Exception e){
            e.printStackTrace();
            Assert.assertTrue("Exception was caught", false);
        }
        stopServer(CONTAINER1);


    }







    public int getListScheduledMessagesSize(String queueName) throws Exception{
        CLI.Result r = runOperation("list-scheduled-messages");
        return r.getResponse().get("result").asList().size();
    }
    public int getListDeliveringMessagesSize(String queueName) throws Exception{
        CLI.Result r = runOperation("list-delivering-messages");
        return r.getResponse().get("result").get(0).get("elements").asList().size();
    }

    private CLI.Result runOperation(final String operation, final String... params) {
        String cmd = CliUtils.buildCommand(ADDRESS, ":" + operation, params);
        return this.cli.executeCommand(cmd);
    }

}
