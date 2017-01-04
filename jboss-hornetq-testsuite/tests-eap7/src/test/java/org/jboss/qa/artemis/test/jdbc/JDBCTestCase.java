package org.jboss.qa.artemis.test.jdbc;

import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageVerifier;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.ConfigurableMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.DuplicatesVerifier;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.LostMessagesVerifier;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.SendReceiveCountVerifier;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.ServerPathUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;

/**
 * Created by eduda on 25.11.2016.
 */
public class JDBCTestCase extends HornetQTestCase {

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.DATABASE, value = "oracle12c"),
            @Param(name = PrepareParams.JOURNAL_BINDINGS_TABLE, value = "node1-bindings-table"),
            @Param(name = PrepareParams.JOURNAL_MESSAGES_TABLE, value = "node1-messages-table"),
            @Param(name = PrepareParams.JOURNAL_LARGE_MESSAGES_TABLE, value = "node1-large-messages-table")
    })
    public void customJDBCTableNames() throws Exception {

        container(1).start();

        ConfigurableMessageVerifier verifier = new ConfigurableMessageVerifier(
                ContainerUtils.getJMSImplementation(container(1)), LostMessagesVerifier.class, DuplicatesVerifier.class, SendReceiveCountVerifier.class);

        ProducerTransAck producer = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, 1);
        producer.setMessageBuilder(new TextMessageBuilder(20));
        producer.addMessageVerifier(verifier);
        addClient(producer);

        ReceiverTransAck receiver = new ReceiverTransAck(container(1), PrepareConstants.QUEUE_JNDI);
        receiver.addMessageVerifier(verifier);
        addClient(receiver);

        producer.start();
        producer.join();

        receiver.start();
        receiver.join();

        container(1).stop();

        Assert.assertNull(producer.getException());
        Assert.assertNull(receiver.getException());
        Assert.assertTrue(verifier.verifyMessages());

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.DATABASE, value = "oracle12c")
    })
    public void removeLocalDataDirectory() throws Exception {

        container(1).start();

        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));

        SubscriberTransAck subscriber = new SubscriberTransAck(container(1), PrepareConstants.TOPIC_JNDI, 10000, 10, 1, "client-0", "subscriber-0");
        addClient(subscriber);
        subscriber.addMessageVerifier(messageVerifier);
        subscriber.subscribe();

        PublisherTransAck publisher = new PublisherTransAck(container(1), PrepareConstants.TOPIC_JNDI, 100, "client-1");
        addClient(publisher);
        publisher.addMessageVerifier(messageVerifier);
        publisher.start();
        publisher.join();
        subscriber.close();

        container(1).stop();
        FileUtils.deleteDirectory(ServerPathUtils.getStandaloneDataDirectory(container(1)));
        container(1).start();

        subscriber.subscribe();
        subscriber.start();
        subscriber.join();

        container(1).stop();

        Assert.assertTrue(messageVerifier.verifyMessages());

    }

}
