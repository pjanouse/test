package org.jboss.qa.hornetq.test.remoting;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Hashtable;
import java.util.Map;

/**
 * Created by eduda on 13.1.2017.
 */
public class JNDILookupTestCase extends HornetQTestCase {

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare("TwoNodes")
    public void multipleURIsTest() throws Exception {

        container(1).start();
        container(2).start();

        Map<String, String> contextProps = JMSTools.getJndiPropertiesToContainers(container(1), container(2));
        Context ctx = null;
        Connection connection = null;

        try {
            ctx = new InitialContext(new Hashtable<String, String>(contextProps));
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(PrepareConstants.REMOTE_CONNECTION_FACTORY_JNDI);
            Queue queue = (Queue) ctx.lookup(PrepareConstants.QUEUE_JNDI);
            connection = cf.createConnection();
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createTextMessage("Test"));
            session.commit();
            producer.close();

            MessageConsumer consumer = session.createConsumer(queue);
            Assert.assertNotNull(consumer.receive(1000));
            consumer.close();
        } finally {
            JMSTools.closeQuitely(ctx);
            JMSTools.closeQuitely(connection);

            container(1).stop();
            container(2).stop();
        }
    }

}
