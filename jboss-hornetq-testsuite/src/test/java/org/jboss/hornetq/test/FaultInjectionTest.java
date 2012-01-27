package org.jboss.hornetq.test;

import org.jboss.arquillian.extension.byteman.api.BMRule;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;



import java.util.HashMap;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.jboss.arquillian.container.test.api.*;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.hornetq.apps.servlets.HornetQTestServlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Before;

@RunWith(Arquillian.class)
public class FaultInjectionTest {

    @ArquillianResource
    ContainerController controller;

    
    int MESSAGES_COUNT = 1000;
    String hostname = "localhost";
    String QUEUE_NAME = "testQueue";

    @Deployment(testable=true)
    public static Archive<?> createDeployment()  {
//        Thread.sleep(50000);
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(HornetQTestServlet.class)
                .addAsWebInfResource("apps/servlets/hornetqtestservlet/web.xml", "web.xml")
                .addAsManifestResource(new StringAsset("Dependencies: org.hornetq\n"), "MANIFEST.MF");
        
    }
    
    

    @Test 
    @BMRule(
            name = "Add another rule mnovak", targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl", targetMethod = "processRoute",
            action = "System.out.println(\"System.out.println(\"Invoke kill by byteman\");killJVM();")
    public void shouldBeAbleToInjectMethodLevelThrowRuleMy() {

        QueueConnection conn = null;
        Queue queue = null;
        QueueSession session = null;

        try {

            ///////////////////////////////////////////////////////////////////////
            ///////// FIXME REPLACE BY REMOTE JNDI LOOKUP
            ////////////////////////////////////////////////////////////////////////
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("host", hostname);
            map.put("port", 5445);

            TransportConfiguration transportConfiguration = new TransportConfiguration(NettyConnectorFactory.class.getName(), map);
            HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, transportConfiguration);
            cf.setRetryInterval(1000);
            cf.setRetryIntervalMultiplier(1.0);
            cf.setReconnectAttempts(1);
            cf.setBlockOnDurableSend(true);
            cf.setBlockOnNonDurableSend(true);
            cf.setInitialConnectAttempts(-1);

            System.out.println("ha: " + cf.getServerLocator().isHA());
            System.out.println("client failure check period is : " + cf.getServerLocator().getClientFailureCheckPeriod());
            System.out.println("client ttl is : " + cf.getConnectionTTL());
            System.out.println("client reconnect attempts is : " + cf.getReconnectAttempts());
            for (String s : transportConfiguration.getParams().keySet()) {
                System.out.println("property: " + s + "  value: " + transportConfiguration.getParams().get(s));
            }
            System.out.println(transportConfiguration);

            //////////////////////////////////////////////////////////////////////////////

            conn = cf.createQueueConnection();

            conn.start();

            queue = HornetQJMSClient.createQueue(QUEUE_NAME);

            session = conn.createQueueSession(false, QueueSession.CLIENT_ACKNOWLEDGE);
            QueueSender sender = session.createSender(queue);

            for (int i = 0; i < MESSAGES_COUNT; i++) {
                Message message = session.createTextMessage("ahoj");

                try {
                    sender.send(message);
                    System.out.println("Producer for node: " + hostname + ". Sent message with property count: "
                            + i + ", messageId:" + message.getJMSMessageID());

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (JMSException ex) {
            ex.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (JMSException ex) {
                    // ignore
                }
            }
        }

    }
}
