package org.jboss.hornetq.test;

import org.jboss.byteman.annotation.BMRule;
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
import java.lang.reflect.Method;
import org.jboss.byteman.rule.RuleInstaller;

@RunWith(Arquillian.class)
public class FaultInjectionTest {

    @ArquillianResource
    ContainerController controller;
    
    @ArquillianResource
    private Deployer deployer;
    
    int MESSAGES_COUNT = 1000;
    
    String hostname = "localhost";
    
    String QUEUE_NAME = "testQueue";
        
    public static final String CONTAINER1 = "clustering-udp-0-unmanaged";
    
    public static final String CONTAINER2 = "clustering-udp-1-unmanaged";
    
    private static final String DEPLOYMENT1 = "dep.container1";

    @Deployment(name = DEPLOYMENT1, managed = false, testable = true)
    public static Archive<?> createDeployment() {

        return ShrinkWrap.create(WebArchive.class).addClasses(HornetQTestServlet.class).addAsWebInfResource("apps/servlets/hornetqtestservlet/web.xml", "web.xml").addAsManifestResource(new StringAsset("Dependencies: org.hornetq\n"), "MANIFEST.MF");

    }

    
    @Test
    @RunAsClient
    @BMRule(name = "Add another rule mnovak", targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl", targetMethod = "processRoute",
    action = "System.out.println(\"Byteman will invoke kill\"); killJVM();")
    public void shouldBeAbleToInjectMethodLevelThrowRuleMy() throws Exception {
        
        controller.start(CONTAINER1);
        
        controller.start(CONTAINER2);
        
        deployer.deploy(DEPLOYMENT1);
        
        // this will install byteman rule
        RuleInstaller.installRule(this.getClass());
       
        // run producer asynchronously
        Thread producer = new Producer();
        // start producer which will lead to kill server by byteman
        producer.start();
        
        // this will wait for kill 
        controller.kill(CONTAINER1);
        
        // start server again 
        controller.start(CONTAINER1);
        
        //and produce messages
        Thread producer2 = new Producer();
        
        producer2.start();
        
        // wait for finish producing
        producer2.join(60000);
       
    }

    class Producer extends Thread {

        public void run() {

            System.out.println("mnovak: start test");
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
}
