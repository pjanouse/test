package org.jboss.qa.hornetq.test.bridges;

import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.specific.NetworkFailuresConstants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;
import org.junit.Assert;
import org.junit.runner.RunWith;

/**
 * @author Miroslav Novak (mnovak@redhat.com)
 */
@RunWith(Arquillian.class)
public class NetworkFailuresJMSBridgesTestCase extends NetworkFailuresBridgesAbstract {

    private static final Logger log = Logger.getLogger(NetworkFailuresJMSBridgesTestCase.class);

    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder, int numberOfFails, boolean staysDisconnected)
            throws Exception {

        startProxies();

        container(2).start(); // B1
        container(1).start(); // A1

        Thread.sleep(5000);
        // message verifier which detects duplicated or lost messages
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));

        // A1 producer
        ProducerTransAck producer1 = new ProducerTransAck(container(1), PrepareConstants.IN_QUEUE_JNDI, NetworkFailuresBridgesAbstract.NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.addMessageVerifier(messageVerifier);
        if (messageBuilder != null) {
            messageBuilder.setAddDuplicatedHeader(true);
            producer1.setMessageBuilder(messageBuilder);
        }
        // B1 consumer
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(2), PrepareConstants.OUT_QUEUE_JNDI,(4 * timeBetweenFails) > 120000 ? (4 * timeBetweenFails) : 120000, 10, 10);

        receiver1.setTimeout(0);
        receiver1.addMessageVerifier(messageVerifier);

        log.info("Start producer and receiver.");
        producer1.start();
        receiver1.start();

        // Wait to send and receive some messages
        Thread.sleep(15 * 1000);

        executeNetworkFails(timeBetweenFails, numberOfFails);

        Thread.sleep(5 * 1000);

        producer1.stopSending();
        producer1.join();
        // Just prints lost or duplicated messages if there are any. This does not fail the test.
      

        if (staysDisconnected)  {
            stopProxies();
            receiver1.join();

            log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
            log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
            messageVerifier.verifyMessages();
            JMSOperations adminOperations = container(1).getJmsOperations();
            int preparedTransactions= adminOperations.getNumberOfPreparedTransaction();
            adminOperations.close();
            if(preparedTransactions>0){
                log.info("There are unfinished transactions in journal, waiting for rollback");
                Thread.sleep(7*60000);
            }



            ReceiverTransAck receiver2 = new ReceiverTransAck(container(1), PrepareConstants.IN_QUEUE_JNDI, 120000, 100, 5);
            receiver2.start();
            receiver2.join();
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getListOfSentMessages().size(),
                    receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size());
        } else {
            receiver1.setReceiveTimeout(120000);
            receiver1.join();
            log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
            log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
            messageVerifier.verifyMessages();


            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        }

        container(2).stop();
        container(1).stop();
    }


    protected void startProxies() throws Exception {

        log.info("Start proxy...");
        if (proxy1 == null) {
            proxy1 = new SimpleProxyServer(container(2).getHostname(), container(2).getHornetqPort(), NetworkFailuresConstants.PROXY_12_PORT,true);
            proxy1.start();
        }
        log.info("Proxy started.");

    }


    protected void stopProxies() throws Exception {
        log.info("Stop proxy...");
        if (proxy1 != null) {
            proxy1.stop();
            proxy1 = null;
        }

        log.info("Proxy stopped.");
    }
}
