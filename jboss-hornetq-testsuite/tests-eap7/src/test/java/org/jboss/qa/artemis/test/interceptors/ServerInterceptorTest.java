package org.jboss.qa.artemis.test.interceptors;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients20.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients20.ReceiverAutoAck;
import org.jboss.qa.hornetq.apps.clients20.ReceiverAutoAckMsgProps;
import org.jboss.qa.hornetq.apps.interceptors.IncomingMessagePacketInterceptor;
import org.jboss.qa.hornetq.apps.interceptors.IncomingOutgoingMessagePacketInterceptor;
import org.jboss.qa.hornetq.apps.interceptors.OutgoingMessagePacketInterceptor;
import category.Functional;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ModuleUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(Arquillian.class)
@Category(Functional.class)
public class ServerInterceptorTest extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ServerInterceptorTest.class);

    public static final String MODULE_NAME = "test.interceptors";

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare("OneNode")
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
    @Prepare("OneNode")
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
    @Prepare("OneNode")
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

        ProducerAutoAck producer = new ProducerAutoAck(container(1), PrepareConstants.QUEUE_JNDI, 100);
        producer.start();
        producer.join();

        List<String> msgProperties = new ArrayList<String>();
        msgProperties.add(prop);

        ReceiverAutoAck receiver = new ReceiverAutoAckMsgProps(container(1), PrepareConstants.QUEUE_JNDI, msgProperties);
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
