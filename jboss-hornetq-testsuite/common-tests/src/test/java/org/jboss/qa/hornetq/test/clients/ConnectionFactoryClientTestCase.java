package org.jboss.qa.hornetq.test.clients;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;

/**
 * @tpChapter Integration testing
 * @tpSubChapter Artemis/HornetQ Connection factory test
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-cluster-ipv6-tests/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-cluster-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class ConnectionFactoryClientTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(ConnectionFactoryClientTestCase.class);

    private static final String QUEUE_NAME = "testQueue";
    private static final String RELATIVE_JNDI_QUEUE = "jms/queue/" + QUEUE_NAME;


    /**
     * @throws Exception
     * @tpTestDetails Client tries to connect directly to HornetQ/Artemis to port 5445. Connection factory is looked
     * up from server. Connection factory is using remote-connector which is using outbound socket binding.
     * @tpPassCrit All messages are delivered to the proper queues and client can connect.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testSendMessagesToMultipleQueues() throws Exception {

        prepareServer();
        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");

        Connection connection = cf.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = (Queue) context.lookup(RELATIVE_JNDI_QUEUE);
        MessageProducer producer = session.createProducer(queue);

        int numberOfMessages = 10;
        for (int i = 0; i < 10; i++) {
            producer.send(session.createTextMessage("message-" + i));
        }

        MessageConsumer consumer = session.createConsumer(queue);
        int count = 0;
        while (consumer.receive(5000) != null) {
            count++;
        }

        Assert.assertEquals("There is different number of send and received messages.", numberOfMessages, count);
        context.close();
        container(1).stop();
    }

    protected void prepareServer() {
        container(1).start();
        JMSOperations jmsOperations = container(1).getJmsOperations();

        jmsOperations.createQueue(QUEUE_NAME, RELATIVE_JNDI_QUEUE, true);

        String socketBindingNameForMessaging = "messaging";
        String outboundSocketBingingName = "outbound-messaging";
        try {
            jmsOperations.removeSocketBinding(socketBindingNameForMessaging);
        } catch (Exception ex) { // ignore
        }
        container(1).restart();
        jmsOperations.createSocketBinding(socketBindingNameForMessaging, 5448);
        jmsOperations.createOutBoundSocketBinding(outboundSocketBingingName, container(1).getHostname(), 5448);
        jmsOperations.close();

        container(1).restart();

        jmsOperations = container(1).getJmsOperations();
        jmsOperations.createRemoteAcceptor("netty-acceptor2", socketBindingNameForMessaging, null);
        jmsOperations.createRemoteConnector("netty-connector2", outboundSocketBingingName, null);
        jmsOperations.setConnectorOnConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, "netty-connector2");

        jmsOperations.close();
        container(1).stop();
    }

}
