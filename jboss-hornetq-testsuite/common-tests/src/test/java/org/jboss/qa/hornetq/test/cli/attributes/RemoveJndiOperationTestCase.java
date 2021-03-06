// TODO ADD TESTS FOR DOMAIN MODE
package org.jboss.qa.hornetq.test.cli.attributes;

import category.Functional;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

/**
 * Created by mnovak on 9/16/14.
 *
 * Test remove Jndi operation.
 *
 */
@Category(Functional.class)
public class RemoveJndiOperationTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(RemoveJndiOperationTestCase.class);

    String queueCoreName1 = "testQueue1";
    String queueCoreName2 = "testQueue2";
    String queueJndiNameRelative1 = "jms/queue/" + queueCoreName1;
    String queueJndiNameRelative2 = "jms/queue/" + queueCoreName2;
    String queueJndiNameFull1 = "java:/" + queueJndiNameRelative1;
    String queueJndiNameFull2 = "java:/" + queueJndiNameRelative2;
    String queueJndiNameFullExported1 = "java:jboss/exported/" + queueJndiNameRelative1;
    String queueJndiNameFullExported2 = "java:jboss/exported/" + queueJndiNameRelative2;

    String topicCoreName1 = "testTopic1";
    String topicCoreName2 = "testTopic2";
    String topicJndiNameRelative1 = "jms/topic/" + topicCoreName1;
    String topicJndiNameRelative2 = "jms/topic/" + topicCoreName2;
    String topicJndiNameFull1 = "java:/" + topicJndiNameRelative1;
    String topicJndiNameFull2 = "java:/" + topicJndiNameRelative2;
    String topicJndiNameFullExported1 = "java:jboss/exported/" + topicJndiNameRelative1;
    String topicJndiNameFullExported2 = "java:jboss/exported/" + topicJndiNameRelative2;

    @Before
    @After
    public void stopServer() {
        container(1).stop();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void createQueueAndRemoveJndiEntry() throws Exception {
        container(1).start();

        JMSOperations jmsOperations = container(1).getJmsOperations();

        jmsOperations.createQueue(queueCoreName1, queueJndiNameRelative1);
        jmsOperations.addQueueJNDIName(queueCoreName1, queueJndiNameFullExported2);

        // remove jndi name
        jmsOperations.removeQueueJNDIName(queueCoreName1, queueJndiNameRelative1);

        checkJNDIEntriesForQueue(jmsOperations, queueCoreName1, queueJndiNameFullExported1, queueJndiNameFullExported2);
        jmsOperations.close();
        container(1).stop();
    }

    private void checkJNDIEntriesForQueue(JMSOperations jmsOperations, String destinationCoreName, String... expectedJNDIEntries) {
        // get jndi entries for destination
        List<String> entries = jmsOperations.getJNDIEntriesForQueue(destinationCoreName);

        // go trough list and check that all of them are there
        for (String currentEntry : entries)    {
            log.info("current entry: " + currentEntry);
            boolean isPresent = false;
            for (String expectedEntry : expectedJNDIEntries) {
                log.info("expected entry: " + expectedEntry);
                if (expectedEntry.equalsIgnoreCase(currentEntry)) {
                    isPresent = true;
                }
            }
            Assert.assertTrue("Entry " + currentEntry + " is not present in expected list. This is probably the JNDI entry which" +
                    " should be removed.", isPresent);
        }

    }
    private void checkJNDIEntriesForTopic(JMSOperations jmsOperations, String destinationCoreName, String... expectedJNDIEntries) {
        // get jndi entries for destination
        List<String> entries = jmsOperations.getJNDIEntriesForTopic(destinationCoreName);

        // go trough list and check that all of them are there
        for (String currentEntry : entries)    {
            boolean isPresent = false;
            for (String expectedEntry : expectedJNDIEntries) {
                if (expectedEntry.equalsIgnoreCase(currentEntry)) {
                    isPresent = true;
                }
            }
            Assert.assertTrue("Entry " + currentEntry + " is not present in expected list. This is probably the JNDI entry which" +
                    " should be removed.", isPresent);
        }

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void createTopicAndRemoveJndiEntry() throws Exception {
        container(1).start();

        JMSOperations jmsOperations = container(1).getJmsOperations();

        jmsOperations.createTopic(topicCoreName1, topicJndiNameRelative1);
        jmsOperations.addTopicJNDIName(topicCoreName1, topicJndiNameFullExported2);

        // remove jndi name
        jmsOperations.removeTpicJNDIName(topicCoreName1, topicJndiNameFullExported2);

        checkJNDIEntriesForTopic(jmsOperations, topicCoreName1, topicJndiNameFullExported1, topicJndiNameRelative1);

        jmsOperations.close();
        container(1).stop();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void createQueueAndRemoveJndiEntryWhichDoesNotExists() {
        container(1).start();

        JMSOperations jmsOperations = container(1).getJmsOperations();

        jmsOperations.createQueue(queueCoreName1, queueJndiNameRelative1);
        jmsOperations.addQueueJNDIName(queueCoreName1, queueJndiNameFullExported2);

        jmsOperations.removeQueueJNDIName(queueCoreName1, queueJndiNameRelative1);

        try {
            jmsOperations.removeQueueJNDIName(queueCoreName1, queueJndiNameRelative1);
            Assert.fail("RuntimeException should be thrown when removing jndi entry which does not exist.");
        } catch (RuntimeException ex)   {
            log.error("RuntimeException was thrown - this is correct in this case.", ex);
        }

        checkJNDIEntriesForQueue(jmsOperations, queueCoreName1, queueJndiNameFullExported1, queueJndiNameFullExported2);

        jmsOperations.close();
        container(1).stop();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void createTopicAndRemoveJndiEntryWhichDoesNotExists() {
        container(1).start();
        JMSOperations jmsOperations = container(1).getJmsOperations();

        jmsOperations.createQueue(topicCoreName1, topicJndiNameRelative1);
        jmsOperations.addQueueJNDIName(topicCoreName1, topicJndiNameFullExported2);

        jmsOperations.removeQueueJNDIName(topicCoreName1, topicJndiNameRelative1);

        try {
            jmsOperations.removeQueueJNDIName(topicCoreName1, topicJndiNameRelative1);
            Assert.fail("RuntimeException should be thrown when removing jndi entry which does not exist.");
        } catch (RuntimeException ex)   {
            log.error("RuntimeException was thrown - this is correct in this case.", ex);
        }

        checkJNDIEntriesForQueue(jmsOperations, topicCoreName1, topicJndiNameFullExported1, topicJndiNameFullExported2);

        jmsOperations.close();
        container(1).stop();
    }
}
