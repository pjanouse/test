package org.jboss.qa.hornetq.test.cli.operations;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.impl.DelayedTextMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;

/**
 * Created by okalman on 2/9/15.
 */

@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class RuntimeQueueOperationsTestCaseCli extends CliTestBase {

    String queueName = "testQueue";
    String queueJndiName = "jms/queue/" + queueName;

    private final String ADDRESS = "/subsystem=messaging/hornetq-server=default/runtime-queue=jms.queue." + queueName;
    private final CliClient cli = new CliClient(new CliConfiguration(container(1).getHostname(), container(1).getPort(),
            container(1).getUsername(), container(1).getPassword()));

    @After
    @Before
    public void stopServerAfterTest() {
        container(1).stop();
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void listDeliveringMessagesTestCase() throws Exception {

        prepareServer(container(1));

        container(1).start();

        Context context = null;
        ConnectionFactory cf = null;
        Connection conn = null;
        Session session = null;
        Queue queue = null;

        int numberOfMessages = 100;
        int commitAfter = 50;

        ProducerAutoAck producer = new ProducerAutoAck(container(1).getHostname(), container(1).getJNDIPort(), queueJndiName, numberOfMessages);
        producer.start();
        producer.join();

        try {
            context = container(1).getContext();

            cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());

            conn = cf.createConnection();

            conn.start();

            queue = (Queue) context.lookup(queueJndiName);

            session = conn.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer receiverTransAck = session.createConsumer(queue);

            int counter = 0;
            int listDeliveringMessagesSize = -1;
            while (receiverTransAck.receive(500) != null) {
                counter++;
                if (counter % commitAfter == 0) {
                    session.commit();
                }
                if (counter == commitAfter) {
                    listDeliveringMessagesSize = this.getListDeliveringMessagesSize();
                }
            }
            receiverTransAck.close();
            ;
            Assert.assertEquals("Number of delivering messages does not match", commitAfter, listDeliveringMessagesSize);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue("Exception was caught", false);


        } finally {
            session.commit();
            session.close();
            conn.close();
        }

        container(1).stop();

    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void listScheduledMessagesTestCase() throws Exception {

        int numberOfMessages = 10;

        prepareServer(container(1));

        container(1).start();
        ProducerAutoAck producer = new ProducerAutoAck(container(1).getHostname(), container(1).getJNDIPort(), queueJndiName, numberOfMessages);
        DelayedTextMessageBuilder delayedTextMessageBuilder = new DelayedTextMessageBuilder(512, 100000);
        producer.setMessageBuilder(delayedTextMessageBuilder);
        producer.start();
        producer.join();
        try {
            Assert.assertEquals("Number of delivering messages does not match", numberOfMessages, getListScheduledMessagesSize());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue("Exception was caught", false);
        }
        container(1).stop();
    }

    private void prepareServer(Container container) {
        container.start();
        JMSOperations ops = container(1).getJmsOperations();
        ops.createQueue(queueName, queueJndiName);
        ops.close();
        container.stop();
    }


    public int getListScheduledMessagesSize() throws Exception {
        CLI.Result r = runOperation("list-scheduled-messages");
        return r.getResponse().get("result").asList().size();
    }

    public int getListDeliveringMessagesSize() throws Exception {
        CLI.Result r = runOperation("list-delivering-messages");
        return r.getResponse().get("result").get(0).get("elements").asList().size();
    }

    private CLI.Result runOperation(final String operation, final String... params) {
        String cmd = CliUtils.buildCommand(ADDRESS, ":" + operation, params);
        return this.cli.executeCommand(cmd);
    }

}
