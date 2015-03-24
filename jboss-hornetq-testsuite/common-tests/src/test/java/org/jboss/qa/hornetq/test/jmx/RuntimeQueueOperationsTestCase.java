package org.jboss.qa.hornetq.test.jmx;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.impl.DelayedTextMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.naming.Context;

/**
 * Created by okalman on 2/9/15.
 */

@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class RuntimeQueueOperationsTestCase extends HornetQTestCase {
    String queueName = "testQueue";
    String queueJndiName = "jms/queue/" + queueName;

    @Before
    public void startServerBeforeTest() {
        container(1).start();
    }

    @After
    public void stopServerAfterTest() {
        container(1).stop();
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
        container(1).start();
        JMSOperations ops = container(1).getJmsOperations();
        ops.createQueue(queueName, queueJndiName);
        ops.close();
        container(1).stop();
        container(1).start();
        ProducerAutoAck producer = new ProducerAutoAck(container(1).getHostname(), container(1).getJNDIPort(),queueJndiName,numberOfMessages);
        producer.start();
        producer.join();



        try {
            context = getContext(container(1).getHostname(), container(1).getJNDIPort());

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
            receiverTransAck.close();
            session.commit();
            session.close();
            conn.close();
            Assert.assertEquals("Number of delivering messages does not match", commitAfter, listDeliveringMessagesSize);



        }catch (Exception e){
            e.printStackTrace();
            Assert.assertTrue("Exception was caught", false);


        }
        container(1).stop();

    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void listScheduledMessagesTestCase() throws Exception{
        int numberOfMessages= 10;

        container(1).start();
        JMSOperations ops = container(1).getJmsOperations();
        ops.createQueue(queueName, queueJndiName);
        ops.close();
        container(1).stop();
        container(1).start();
        ProducerAutoAck producer = new ProducerAutoAck(container(1).getHostname(), container(1).getJNDIPort(),queueJndiName,numberOfMessages);
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
        container(1).stop();


    }


    public int getListScheduledMessagesSize(String queueName) throws Exception{
        JMXConnector connector = null;
        CompositeData[] elements=null;
        CompositeData[] resultMap=null;
        try {

            connector = jmxUtils.getJmxConnectorForEap(CONTAINER1_INFO);
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName objectName = new ObjectName("jboss.as:subsystem=messaging,hornetq-server=default,runtime-queue=jms.queue." + queueName);
            resultMap = (CompositeData[]) mbeanServer.invoke(objectName, "listScheduledMessages", new Object[]{}, new String[]{});

        }finally {
            connector.close();
        }

        return resultMap.length;



    }

    public int getListDeliveringMessagesSize(String queueName) throws Exception{
        JMXConnector connector = null;
        CompositeData[] elements=null;
        try {

            connector = jmxUtils.getJmxConnectorForEap(CONTAINER1_INFO);
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName objectName = new ObjectName("jboss.as:subsystem=messaging,hornetq-server=default,runtime-queue=jms.queue." + queueName);
            CompositeData[] resultMap = (CompositeData[]) mbeanServer.invoke(objectName, "listDeliveringMessages", new Object[]{}, new String[]{});
            elements = (CompositeData[]) resultMap[0].get("elements");
        }finally {
                connector.close();
        }

        return elements.length;




    }

}
