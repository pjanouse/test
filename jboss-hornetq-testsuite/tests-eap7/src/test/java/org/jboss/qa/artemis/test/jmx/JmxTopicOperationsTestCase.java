package org.jboss.qa.artemis.test.jmx;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;

import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients20.PublisherAutoAck;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;


import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.Topic;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Properties;

/**
 * Created by okalman on 1/15/16.
 */

@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class JmxTopicOperationsTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(JmxTopicOperationsTestCase.class);
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare("OneNode")
    public void listDurableSubscriptionsAsJsonWithSubscriber() throws Exception{
        container(1).start();
        PublisherAutoAck publisherAutoAck = new PublisherAutoAck(container(1), PrepareBase.TOPIC_JNDI, 1, "pub");
        publisherAutoAck.start();
        publisherAutoAck.join();

        Thread t= new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Properties env = new Properties();
                    env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
                    env.put(Context.PROVIDER_URL, String.format("%s%s:%s", "http-remoting://", "127.0.0.1", 8080));
                    Context context = new InitialContext(env);
                    ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");
                    Topic topic = (Topic) context.lookup(PrepareBase.TOPIC_JNDI);
                    JMSContext ctx = cf.createContext();
                    JMSConsumer subscriber = ctx.createSharedDurableConsumer(topic,"ASIDE-FullStatus@RCD_NMS");
                    subscriber.receive(30000);
                }catch(Exception e){

                }
            }
        });
        t.start();
        Thread.sleep(1000);
        JMXConnector connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));

        logger.info("Getting queue controller from JMX");
        MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
        ObjectName topicControler = ObjectName.getInstance(
                "jboss.as:subsystem=messaging-activemq,server=default,jms-topic=" + PrepareBase.TOPIC_NAME);

        logger.info("Invoking queue listDurableSubscriptionsAsJson via JMX");

        Object o = mbeanServer.invoke(topicControler, "listDurableSubscriptionsAsJson", null, null);
        Assert.assertFalse("Result contains exception", ((String)o).contains("Exception"));
        container(1).stop();
    }
}
