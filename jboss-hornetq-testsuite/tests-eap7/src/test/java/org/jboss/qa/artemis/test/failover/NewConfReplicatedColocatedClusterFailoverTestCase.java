package org.jboss.qa.artemis.test.failover;

import category.FailoverColocatedClusterNewConf;
import org.apache.log4j.Logger;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ArtemisJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF  STANDALONE JMS CLIENT WITH REPLICATED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster-replicated-journal/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster-replicated-journal-win/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 * @tpTestCaseDetails This test case implements  all tests from ColocatedClusterFailoverTestCase class with only one
 * difference in these tests: testKillInClusterLargeMessages, testShutdownInClusterLargeMessages, testShutdownInClusterSmallMessages,
 * testKillInClusterSmallMessages. Difference is, that node-2 is not started after kill/shutdown.
 */
@Ignore
@RunWith(Arquillian.class)
@Category(FailoverColocatedClusterNewConf.class)
public class NewConfReplicatedColocatedClusterFailoverTestCase extends NewConfColocatedClusterFailoverTestCase {


    private static final Logger logger = Logger.getLogger(NewConfReplicatedColocatedClusterFailoverTestCase.class);

    /**
     * Prepare two servers in colocated topology in cluster.
     */
    public void prepareColocatedTopologyInCluster() {

        prepareCollocatedLiveServer(container(1), "ASYNCIO", Constants.CONNECTOR_TYPE.NETTY_NIO);
        prepareCollocatedLiveServer(container(2), "ASYNCIO", Constants.CONNECTOR_TYPE.NETTY_NIO);
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container     The container - defined in arquillian.xml
     * @param journalType   ASYNCIO, NIO
     * @param connectorType whether to use NIO in connectors for CF or old blocking IO, or http connector
     */
    protected void prepareCollocatedLiveServer(Container container, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        setConnectorForClient(container, connectorType);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyCollocatedReplicated("default", 1000, -1, 1000, 1, true, null);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }
        jmsAdminOperations.createQueue("default", inQueueName, inQueue, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueue, true);


        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * In case of replication we don't want to start second server again. We expect that colocated backup
     * will have all load-balanced messages.
     *
     * @param shutdown
     * @param messageBuilder
     * @throws Exception
     */
    public void testFailInCluster(boolean shutdown, MessageBuilder messageBuilder) throws Exception {

        prepareColocatedTopologyInCluster();

        container(1).start();
        container(2).start();

        // give some time for servers to find each other
        CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);
        CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueue, numberOfMessages);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(1000);
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ArtemisJMSImplementation.getInstance());
        producerToInQueue1.addMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();

        logger.info("########################################");
        logger.info("kill - second server");
        logger.info("########################################");

        if (shutdown) {
            container(2).stop();
        } else {
            container(2).kill();
        }

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), inQueue, 30000, 1000, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.setAckAfter(1000);

        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();

    }

}
