package org.jboss.qa.artemis.test.httpconnector;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.DebugTools;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class BatchDelayTestCase extends HornetQTestCase {

    private static final String QUEUE_NAME = "testQueue";

    private static final String QUEUE_JNDI = "jms/queue/" + QUEUE_NAME;

    private static final String HTTP_CONNECTOR_NAME = "http-connector";

    private static final String HTTP_ACCEPTOR_NAME = "http-acceptor";

    private static final String REMOTE_CONNECTOR_NAME = "remote-connector";

    private static final String REMOTE_ACCEPTOR_NAME = "remote-acceptor";

    private static final String SOCKET_BINDING_NAME = "messaging";

    private static final String REMOTE_CONNECTION_FACTORY_NAME = "RemoteConnectionFactory";

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void httpBatchDelayZeroTest() throws Exception {
        Container container = container(1);
        container.startAdminOnly();

        JMSOperations jmsOperations = container.getJmsOperations();

        Map<String, String> params = new HashMap<>();
        params.put("batch-delay", "0");

        jmsOperations.setParamsForHttpAcceptor(HTTP_ACCEPTOR_NAME, params);
        jmsOperations.setParamsForHttpConnector(HTTP_CONNECTOR_NAME, params);

        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI);

        jmsOperations.close();

        container.stop();

        testLogic();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void httpBatchDelayNonZeroTest() throws Exception {
        Container container = container(1);
        container.startAdminOnly();

        JMSOperations jmsOperations = container.getJmsOperations();

        Map<String, String> params = new HashMap<>();
        params.put("batch-delay", "50");

        jmsOperations.setParamsForHttpAcceptor(HTTP_ACCEPTOR_NAME, params);
        jmsOperations.setParamsForHttpConnector(HTTP_CONNECTOR_NAME, params);

        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI);

        jmsOperations.close();

        container.stop();

        testLogic();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void nettyBatchDelayZeroTest() throws Exception {
        Container container = container(1);
        container.startAdminOnly();

        JMSOperations jmsOperations = container.getJmsOperations();

        Map<String, String> params = new HashMap<>();
        params.put("batch-delay", "0");

        jmsOperations.createSocketBinding(SOCKET_BINDING_NAME, 5445);
        jmsOperations.createRemoteAcceptor(REMOTE_ACCEPTOR_NAME, SOCKET_BINDING_NAME, params);
        jmsOperations.createRemoteConnector(REMOTE_CONNECTOR_NAME, SOCKET_BINDING_NAME, params);
        jmsOperations.setConnectorOnConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, REMOTE_CONNECTOR_NAME);

        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI);

        jmsOperations.close();

        container.stop();

        testLogic();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void nettyBatchDelayNonZeroTest() throws Exception {
        Container container = container(1);
        container.startAdminOnly();

        JMSOperations jmsOperations = container.getJmsOperations();

        Map<String, String> params = new HashMap<>();
        params.put("batch-delay", "50");

        jmsOperations.createSocketBinding(SOCKET_BINDING_NAME, 5445);
        jmsOperations.createRemoteAcceptor(REMOTE_ACCEPTOR_NAME, SOCKET_BINDING_NAME, params);
        jmsOperations.createRemoteConnector(REMOTE_CONNECTOR_NAME, SOCKET_BINDING_NAME, params);
        jmsOperations.setConnectorOnConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, REMOTE_CONNECTOR_NAME);

        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI);

        jmsOperations.close();

        container.stop();

        testLogic();
    }

    private void testLogic() throws Exception {
        final Container container = container(1);
        container.start();

        ReceiverAutoAck receiver = new ReceiverAutoAck(container, QUEUE_JNDI, 1000, 1);
        addClient(receiver);
        receiver.start();
        receiver.join(5000);

        if (!receiver.isAlive()) {
            DebugTools.printThreadDump();
            ContainerUtils.printThreadDump(container);
            Assert.fail();
        }

        container.stop();
    }

}
