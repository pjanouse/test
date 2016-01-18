package org.jboss.qa.artemis.test.interceptors;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients20.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients20.ReceiverAutoAck;
import org.jboss.qa.hornetq.apps.clients20.ReceiverAutoAckMsgProps;
import org.jboss.qa.hornetq.apps.interceptors.IncomingMessagePacketInterceptor;
import org.jboss.qa.hornetq.apps.interceptors.IncomingOutgoingMessagePacketInterceptor;
import org.jboss.qa.hornetq.apps.interceptors.OutgoingMessagePacketInterceptor;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ModuleUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.testng.Assert;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class ServerInterceptorTest extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ServerInterceptorTest.class);

    public static final String MODULE_NAME = "test.interceptors";

    public static final String QUEUE_NAME = "testQueue";

    public static final String QUEUE_JNDI = "jms/queue/" + QUEUE_NAME;

    protected void prepareServer(Container container) {
        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();
        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI);
        jmsOperations.close();
        container.stop();
    }

    @After
    public void stopServers() {
        container(1).stop();
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void incomingInterceptorTest() throws Exception {
        interceptorTest(
                IncomingMessagePacketInterceptor.class,
                IncomingMessagePacketInterceptor.CHECK_PROP,
                IncomingMessagePacketInterceptor.CHECK_VALUE,
                InterceptorType.INCOMING);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void outgoingInterceptorTest() throws Exception {
        interceptorTest(
                OutgoingMessagePacketInterceptor.class,
                OutgoingMessagePacketInterceptor.CHECK_PROP,
                OutgoingMessagePacketInterceptor.CHECK_VALUE,
                InterceptorType.OUTGOING);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void bothInterceptorTest() throws Exception {
        interceptorTest(
                IncomingOutgoingMessagePacketInterceptor.class,
                IncomingOutgoingMessagePacketInterceptor.CHECK_PROP,
                IncomingOutgoingMessagePacketInterceptor.CHECK_VALUE,
                InterceptorType.BOTH);
    }

    protected void interceptorTest(Class clazz, String prop, String value, InterceptorType interceptorType) throws Exception {
        List<Class> classes = new ArrayList<Class>();
        classes.add(clazz);
        List<String> dependencies = new ArrayList<String>();
        dependencies.add("org.apache.activemq.artemis");
        ModuleUtils.registerModule(container(1), MODULE_NAME, classes, dependencies);
        prepareServer(container(1));

        container(1).startAdminOnly();
        JMSOperations jmsOperations = container(1).getJmsOperations();

        if (interceptorType == InterceptorType.INCOMING) {
            jmsOperations.addIncomingInterceptor(MODULE_NAME, clazz.getCanonicalName());
        } else if (interceptorType == InterceptorType.OUTGOING) {
            jmsOperations.addOutgoingInterceptor(MODULE_NAME, clazz.getCanonicalName());
        } else if (interceptorType == InterceptorType.BOTH) {
            jmsOperations.addIncomingInterceptor(MODULE_NAME, clazz.getCanonicalName());
            jmsOperations.addOutgoingInterceptor(MODULE_NAME, clazz.getCanonicalName());
        }
        jmsOperations.close();
        container(1).stop();

        container(1).start();

        ProducerAutoAck producer = new ProducerAutoAck(container(1), QUEUE_JNDI, 100);
        producer.start();
        producer.join();

        List<String> msgProperties = new ArrayList<String>();
        msgProperties.add(prop);

        ReceiverAutoAck receiver = new ReceiverAutoAckMsgProps(container(1), QUEUE_JNDI, msgProperties);
        receiver.start();
        receiver.join();

        for (Map<String, String> msg : receiver.getListOfReceivedMessages()) {
            Assert.assertEquals(msg.get(prop), value);
        }

        container(1).stop();
    }

    private enum InterceptorType {
        INCOMING,
        OUTGOING,
        BOTH
    }
}
