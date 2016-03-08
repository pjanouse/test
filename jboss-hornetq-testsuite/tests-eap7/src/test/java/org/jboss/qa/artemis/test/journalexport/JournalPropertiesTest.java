package org.jboss.qa.artemis.test.journalexport;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients20.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients20.ReceiverAutoAck;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.testng.Assert;

import java.io.File;

@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class JournalPropertiesTest extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(JournalPropertiesTest.class);

    public static final String queueName = "testQueue";

    public static final String queueJNDI = "jms/queue/" + queueName;

    public static final int JOURNAL_POOL_FILES = 15;

    @After
    public void stopServer() {
        container(1).stop();
    }

    protected void prepareServer(Container container) {
        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();
        jmsOperations.createQueue(queueName, queueJNDI);
        jmsOperations.setJournalDirectory(JOURNAL_DIRECTORY_A );
        jmsOperations.setBindingsDirectory(JOURNAL_DIRECTORY_A);
        jmsOperations.setPagingDirectory(JOURNAL_DIRECTORY_A);
        jmsOperations.setLargeMessagesDirectory(JOURNAL_DIRECTORY_A);
        jmsOperations.setJournalFileSize(10 * 1024);
        jmsOperations.setJournalMinFiles(2);
        jmsOperations.setJournalPoolFiles(JOURNAL_POOL_FILES);
        jmsOperations.setJournalCompactMinFiles(JOURNAL_POOL_FILES);
        jmsOperations.close();
        container.stop();
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void journalPoolFilesAttributeTest() throws InterruptedException {
        final int NUM_MESSAGES = 100;

        prepareServer(container(1));
        container(1).start();

        ProducerAutoAck producer = new ProducerAutoAck(container(1), queueJNDI, NUM_MESSAGES);
        producer.start();
        producer.join();
        Assert.assertEquals(producer.getCount(), NUM_MESSAGES, "Producer does not send all messages.");

        Assert.assertTrue(numberOfFilesInJournal() > JOURNAL_POOL_FILES, "Number of files in journal is not greater than journal-pool-files param");

        ReceiverAutoAck receiver = new ReceiverAutoAck(container(1), queueJNDI);
        receiver.start();
        receiver.join();
        Assert.assertEquals(receiver.getCount(), NUM_MESSAGES, "Different number sent and received messages.");

        Assert.assertTrue(numberOfFilesInJournal() == JOURNAL_POOL_FILES, "Number of files in journal is not equal to journal-pool-files param");

        container(1).stop();
    }

    private int numberOfFilesInJournal() {
        File journalDir = new File(JOURNAL_DIRECTORY_A, "journal");
        Assert.assertTrue(journalDir.exists(), "Journal directory does not exists");
        Assert.assertTrue(journalDir.isDirectory(), "Journal directory is not a directory");

        int num = 0;
        for (File file : journalDir.listFiles()) {
            if (file.getName().startsWith("activemq-data")) {
                num++;
            }
        }
        return num;
    }

}
