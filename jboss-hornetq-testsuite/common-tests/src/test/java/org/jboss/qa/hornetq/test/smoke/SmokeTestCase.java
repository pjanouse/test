package org.jboss.qa.hornetq.test.smoke;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.ConfigurableMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.DuplicatesVerifier;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.LostMessagesVerifier;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.SendReceiveCountVerifier;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(FunctionalTests.class)
public class SmokeTestCase extends HornetQTestCase {

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare("OneNode")
    public void sendLargeMessage() throws Exception {

        container(1).start();

        ConfigurableMessageVerifier verifier = new ConfigurableMessageVerifier(
                ContainerUtils.getJMSImplementation(container(1)), LostMessagesVerifier.class, DuplicatesVerifier.class, SendReceiveCountVerifier.class);

        ProducerTransAck producer = new ProducerTransAck(container(1), PrepareBase.QUEUE_JNDI, 1);
        producer.setMessageBuilder(new TextMessageBuilder(200 * 1024));
        producer.addMessageVerifier(verifier);
        addClient(producer);

        ReceiverTransAck receiver = new ReceiverTransAck(container(1), PrepareBase.QUEUE_JNDI);
        receiver.addMessageVerifier(verifier);
        addClient(receiver);

        producer.start();
        producer.join();

        receiver.start();
        receiver.join();

        container(1).stop();

        Assert.assertTrue(verifier.verifyMessages());

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare("OneNode")
    public void sendManyMessagesAndRestart() throws Exception {
        container(1).start();

        ConfigurableMessageVerifier verifier = new ConfigurableMessageVerifier(
                ContainerUtils.getJMSImplementation(container(1)), LostMessagesVerifier.class, DuplicatesVerifier.class, SendReceiveCountVerifier.class);

        ProducerTransAck producer = new ProducerTransAck(container(1), PrepareBase.QUEUE_JNDI, 10000);
        producer.setCommitAfter(1000);
        producer.setMessageBuilder(new TextMessageBuilder(10));
        producer.addMessageVerifier(verifier);
        addClient(producer);

        ReceiverTransAck receiver = new ReceiverTransAck(container(1), PrepareBase.QUEUE_JNDI, 30000, 1000, 1);
        receiver.addMessageVerifier(verifier);
        addClient(receiver);

        producer.start();
        producer.join();

        container(1).restart();

        receiver.start();
        receiver.join();

        container(1).stop();

        Assert.assertTrue(verifier.verifyMessages());
    }

}
