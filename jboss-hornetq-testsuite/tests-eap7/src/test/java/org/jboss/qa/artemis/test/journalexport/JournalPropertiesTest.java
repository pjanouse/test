package org.jboss.qa.artemis.test.journalexport;

import category.Functional;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JournalDirectory;
import org.jboss.qa.hornetq.apps.clients20.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients20.ReceiverAutoAck;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(Arquillian.class)
@Category(Functional.class)
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
//        jmsOperations.setJournalPoolFiles(JOURNAL_POOL_FILES);
//        jmsOperations.setJournalCompactMinFiles(JOURNAL_POOL_FILES);
        jmsOperations.close();
        container.stop();
    }

    protected void setupCompacting(Container container) {
        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();
        jmsOperations.setJournalPoolFiles(JOURNAL_POOL_FILES);
        jmsOperations.setJournalCompactMinFiles(JOURNAL_POOL_FILES);
        jmsOperations.setJournalCompactPercentage(100);
        jmsOperations.close();
        container.stop();
    }



    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void journalPoolFilesAttributeTest() throws InterruptedException {
        Assume.assumeFalse(prepareCoordinator.getParams().containsKey(PrepareParams.DATABASE));

        final int NUM_MESSAGES = 100;

        prepareServer(container(1));
        container(1).start();

        ProducerAutoAck producer = new ProducerAutoAck(container(1), queueJNDI, NUM_MESSAGES);
        producer.start();
        producer.join();
        Assert.assertEquals("Producer does not send all messages.", producer.getCount(), NUM_MESSAGES);

        int journalFilesAll = numberOfFilesInJournal();
        log.info("Number of journal files after all messages send " + journalFilesAll);
        Assert.assertTrue("Number of files in journal is not greater than journal-pool-files param", journalFilesAll > JOURNAL_POOL_FILES);

        ReceiverAutoAck receiver = new ReceiverAutoAck(container(1), queueJNDI);
        receiver.start();
        receiver.join();
        Assert.assertEquals("Different number sent and received messages.", receiver.getCount(), NUM_MESSAGES);

        container(1).stop();
        setupCompacting(container(1));
        container(1).start();

        ProducerAutoAck producer2 = new ProducerAutoAck(container(1), queueJNDI, 10);
        producer2.start();
        producer2.join();

        Assert.assertEquals("Number of files in journal is not equal to journal-pool-files param", numberOfFilesInJournal(), JOURNAL_POOL_FILES);

        container(1).stop();
    }

    private int numberOfFilesInJournal() {

        File journalDir = new File(JournalDirectory.getJournalDirectory(container(1).getServerHome(), JOURNAL_DIRECTORY_A), "journal");
        Assert.assertTrue("Journal directory does not exists", journalDir.exists());
        Assert.assertTrue("Journal directory is not a directory", journalDir.isDirectory());

        int num = 0;
        for (File file : journalDir.listFiles()) {
            if (file.getName().startsWith("activemq-data")) {
                num++;
            }
        }
        return num;
    }

}
