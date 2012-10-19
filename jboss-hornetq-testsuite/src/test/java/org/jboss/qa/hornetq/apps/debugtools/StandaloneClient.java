package org.jboss.qa.hornetq.apps.debugtools;

import org.jboss.qa.hornetq.apps.clients.SimpleJMSClient;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsAutoAck;
import org.jboss.qa.hornetq.test.HornetQTestCaseConstants;

import javax.jms.Session;

/**
 * TODO
 */
public class StandaloneClient {
    public static void main(String[] args) {
        try {
            /**
            SimpleJMSClient client = new SimpleJMSClient("127.0.0.1", 1099, 30, Session.AUTO_ACKNOWLEDGE, false);
            client.setInitialContextClass("org.jnp.interfaces.NamingContextFactory");
            client.setConnectionFactoryName("/ConnectionFactory");
            client.sendMessages("TestTopic");
             ***/              /**
            SubscriberAutoAck topicClient = new SubscriberAutoAck(
                    HornetQTestCaseConstants.EAP5_CONTAINER,
                    "localhost", 1099, "testXTopic0", "id1", "name1");
            topicClient.subscribe();

            topicClient = new SubscriberAutoAck(
                    HornetQTestCaseConstants.EAP5_CONTAINER,
                    "localhost", 1099, "testXTopic0", "id2", "name2");
            topicClient.subscribe();
             **/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
