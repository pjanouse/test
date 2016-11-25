package org.jboss.qa.artemis.test.jdbc;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.ConfigurableMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.DuplicatesVerifier;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.LostMessagesVerifier;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.SendReceiveCountVerifier;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;

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

        ProducerTransAck producer = new ProducerTransAck(container(1), PrepareBase.QUEUE_JNDI, 1);
        producer.setMessageBuilder(new TextMessageBuilder(20));
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

        Assert.assertNull(producer.getException());
        Assert.assertNull(receiver.getException());
        Assert.assertTrue(verifier.verifyMessages());

    }

}
