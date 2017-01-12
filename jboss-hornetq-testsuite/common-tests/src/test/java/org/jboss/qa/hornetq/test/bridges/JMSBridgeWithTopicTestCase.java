package org.jboss.qa.hornetq.test.bridges;

import category.JournalAndBridgeCompatibility;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberClientAck;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.Assert;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mstyk@redhat.com
 * @tpChapter Backward compatibility testing
 * @tpSubChapter COMPATIBILITY OF JMS BRIDGES - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-integration-journal-and-jms-bridge-compatibility-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 */
@Category(JournalAndBridgeCompatibility.class)
public class JMSBridgeWithTopicTestCase extends JMSBridgeTestCase {

    // Topic to send messages in
    private String inTopicName = "InTopic";
    private String inTopicJndiName = "jms/topic/" + inTopicName;
    // Topic for receive messages out
    private String outTopicName = "OutTopic";
    private String outTopicJndiName = "jms/topic/" + outTopicName;

    @Override
    protected void sendReceiveSubTest(Container inServer, Container outServer) throws Exception {

        List<FinalTestMessageVerifier> verifiers = new ArrayList<FinalTestMessageVerifier>();
        verifiers.add(messageVerifier);

        PublisherClientAck producer = new PublisherClientAck(inServer,
                inTopicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "pub1");
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.setMessageVerifiers(verifiers);
        producer.start();

        SubscriberClientAck receiver = new SubscriberClientAck(outServer,
                outTopicJndiName, 10000, 100, 10, "subID", "sub1");
        receiver.addMessageVerifier(messageVerifier);
        receiver.start();
        receiver.join();
        producer.join();

        logger.info("Publisher " + producer.getListOfSentMessages().size());
        logger.info("Subscriber: " + receiver.getListOfReceivedMessages().size());

        messageVerifier.verifyMessages();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer.getListOfSentMessages().size(), receiver.getListOfReceivedMessages().size());
        Assert.assertTrue("No send messages.", producer.getListOfSentMessages().size() > 0);

    }

    @Override
    protected String getSourceDestination(){
        return inTopicJndiName;
    }

    @Override
    protected String getTargetDestination(){
        return outTopicJndiName;
    }

    @Override
    protected void createDestinations(JMSOperations jmsAdminOperations) {
        jmsAdminOperations.createTopic("default", inTopicName, inTopicJndiName);
        jmsAdminOperations.createTopic("default", outTopicName, outTopicJndiName);
    }

}
