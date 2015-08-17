package org.jboss.qa.hornetq.test.transportprotocols;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Testing compression of messages
 *
 * @tpChapter Functional testing
 * @tpSubChapter MESSAGE COMPRESSION - TEST SCENARIOS
 *
 * @tpJobLink
 * https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-functional-tests
 * @tpJobLink
 * https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink
 * https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 *
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class MessageCompressionTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(MessageCompressionTestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 200;

    String queueName = "testQueue";
    String queueJndiName = "jms/queue/testQueue";

    @After
    public void stopAllServers() {

        container(1).stop();

    }

    /**
     *
     * @tpTestDetails Start server and send 200 messages to queue (mix of small
     * and large messages).Once producer finishes, try to receive messages in
     * transacted session.
     * @tpProcedure <ul>
     * <li>start one server with deployed queue and enabled message
     * compression</li>
     * <li>send messages to queue - mix of small and large messages</li>
     * <li>start consumer with transacted session which receives messages</li>
     * </ul>
     * @tpPassCrit check that all messages were correctly received
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testCompression() throws Exception {
        prepareServer(container(1));

        container(1).start();
        // Send messages into input node and read from output node
        ProducerTransAck producer = new ProducerTransAck(container(1), queueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setMessageBuilder(new ClientMixMessageBuilder(10, 1024 * 10)); // large messages have 100MB
        ReceiverTransAck receiver = new ReceiverTransAck(container(1), queueJndiName, 10000, 10, 10);

        logger.info("Start producer and consumer.");
        producer.start();
        receiver.start();

        producer.join();
        receiver.join();

        Assert.assertEquals("Number of sent and received messages is different. Sent: " + producer.getListOfSentMessages().size()
                + "Received: " + receiver.getListOfReceivedMessages().size(), producer.getListOfSentMessages().size(),
                receiver.getListOfReceivedMessages().size());
        Assert.assertFalse("Producer did not sent any messages. Sent: " + producer.getListOfSentMessages().size(), producer.getListOfSentMessages().size() == 0);
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size() == 0);
        Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + NUMBER_OF_MESSAGES_PER_PRODUCER
                + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size(), NUMBER_OF_MESSAGES_PER_PRODUCER);

        container(1).stop();
    }

    /**
     * Test all possible things. Failed operation simply throw RuntimeException
     *
     * @param container Test container - defined in arquillian.xml
     */
    public void prepareServer(Container container) throws IOException {

        String connectionFactoryName = "RemoteConnectionFactory";
        String serverName = "default";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        if (ContainerUtils.isEAP6(container)) {
            jmsAdminOperations.setClustered(true);
        }
        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSecurityEnabled(false);
        // set compression of large messages
        jmsAdminOperations.setCompressionOnConnectionFactory(connectionFactoryName, true);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 5000 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.createQueue(serverName, queueName, queueJndiName, true);
        jmsAdminOperations.close();
        container.stop();
    }

}
