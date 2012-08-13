package org.jboss.qa.hornetq.apps.debugtools;

import org.jboss.qa.hornetq.apps.clients.SimpleJMSClient;

import javax.jms.Session;

/**
 * TODO
 */
public class StandaloneClient {
    public static void main(String[] args) {
        try {
            SimpleJMSClient client = new SimpleJMSClient("127.0.0.1", 1099, 30, Session.AUTO_ACKNOWLEDGE, false);
            client.setInitialContextClass("org.jnp.interfaces.NamingContextFactory");
            client.setConnectionFactoryName("/ConnectionFactory");
            client.sendMessages("InQueue");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
