package org.jboss.qa.hornetq.test.persistence.zero;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixedMessageTypeBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;

/**
 * Created by mnovak
 */
@Category(FunctionalTests.class)
public class ZeroPersistenceTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(ZeroPersistenceTestCase.class);

    protected final static int NORMAL_MESSAGE_SIZE_BYTES = 1;
    protected final static int LARGE_MESSAGE_SIZE_BYTES = 150;

    protected final static String MAX_SIZE_BYTES = "1048576"; // 1024 * 1024 = 1MB
    protected final static String PAGE_SIZE_BYTES = "409600"; // 400 * 1024 = 400KB

    /**
     * @tpTestDetails Start server with disabled persistence and address-full-policy set to "PAGE"
     * @tpPassCrit All messages are successfully received according to priority.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "PAGE"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = MAX_SIZE_BYTES),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = PAGE_SIZE_BYTES),
            @Param(name = PrepareParams.PERSISTENCE_ENABLED, value = "false")
    })
    public void testZeroPersistenceNormalMessagesPage() throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        testZeroPersistence(messageBuilder);
    }

    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "PAGE"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = MAX_SIZE_BYTES),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = PAGE_SIZE_BYTES),
            @Param(name = PrepareParams.PERSISTENCE_ENABLED, value = "false")
    })
    public void testZeroPersistenceLargeMessagesPage() throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES);
        testZeroPersistence(messageBuilder);
    }

    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = MAX_SIZE_BYTES),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = PAGE_SIZE_BYTES),
            @Param(name = PrepareParams.PERSISTENCE_ENABLED, value = "false")
    })
    public void testZeroPersistenceNormalMessagesBlock() throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        testZeroPersistence(messageBuilder);
    }

    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = MAX_SIZE_BYTES),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = PAGE_SIZE_BYTES),
            @Param(name = PrepareParams.PERSISTENCE_ENABLED, value = "false")
    })
    public void testZeroPersistenceLargeMessagesBlock() throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES);
        testZeroPersistence(messageBuilder);
    }

    public void testZeroPersistence(MessageBuilder messageBuilder) throws Exception {

        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));

        container(1).start();

        // send messages to queue
        ProducerTransAck producer1 = new ProducerTransAck(container(1), PrepareConstants.IN_QUEUE_JNDI, Integer.MAX_VALUE);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setCommitAfter(10);
        producer1.setTimeout(0);
        producer1.addMessageVerifier(messageVerifier);
        addClient(producer1);
        producer1.start();

        // as long as producer can send message then send
        new JMSTools().waitForMessages(PrepareConstants.IN_QUEUE_NAME, 200, 60000, container(1));
        long count = 0;
        long newCount = 0;
        while ((newCount = new JMSTools().countMessages(PrepareConstants.IN_QUEUE_NAME, container(1))) > count)  {
            count = newCount;
            Thread.sleep(10000);
        }
        logger.info("No more message can get to queue - " + PrepareConstants.IN_QUEUE_NAME + ". Start consumer.");

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), PrepareConstants.IN_QUEUE_JNDI, 20000, 10, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.setTimeout(0);
        addClient(receiver1);
        receiver1.start();

        new JMSTools().waitForAtLeastOneReceiverToConsumeNumberOfMessages(Arrays.asList(new Client[] {receiver1}), 200, 60000);

        producer1.stopSending();
        producer1.join();
        receiver1.join();

        Assert.assertTrue("Lost/Duplicated messages were found. Check test logs for more details.", messageVerifier.verifyMessages());

        container(1).stop();
    }
}
