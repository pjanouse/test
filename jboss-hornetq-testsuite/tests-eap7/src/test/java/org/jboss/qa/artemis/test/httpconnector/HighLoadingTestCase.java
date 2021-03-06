package org.jboss.qa.artemis.test.httpconnector;

import category.Functional;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.PublisherAutoAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static junit.framework.Assert.fail;

/**
 * Test case covers tests which creates many connections on http connector and
 * test if all of them are served as expected.
 *
 * @author eduda@redhat.com
 * @tpChapter Functional testing
 * @tpSubChapter HTTP CONNECTOR - TEST SCENARIOS
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(Functional.class)
public class HighLoadingTestCase extends HornetQTestCase {

    @Before
    @After
    public void stopAllServers() {
        container(1).stop();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare("OneNode")
    public void manyDurableSubscribersTest() {

        final int numberOfMessages = 10;
        final int subscribersCount = 100;

        container(1).start();

        try {
            // Publisher
            PublisherAutoAck publisher = new PublisherAutoAck(container(1), PrepareConstants.TOPIC_JNDI, numberOfMessages, "publisher");

            // Consumers
            SubscriberAutoAck[] subscribers = new SubscriberAutoAck[subscribersCount];
            for (int i = 0; i < subscribersCount; i++) {
                subscribers[i] = new SubscriberAutoAck(container(1), PrepareConstants.TOPIC_JNDI, "client" + i, "subscriber" + i);
                subscribers[i].subscribe();
            }

            publisher.start();

            for (SubscriberAutoAck subscriber: subscribers) {
                subscriber.start();
            }

            publisher.join();

            for (SubscriberAutoAck subscriber : subscribers) {
                subscriber.join();
            }

            if (publisher.getListOfSentMessages().size() != numberOfMessages) {
                fail("Publisher did not send defined count of messages.");
            } else {
                for (SubscriberAutoAck subscriber : subscribers) {
                    if (subscriber.getListOfReceivedMessages().size() != numberOfMessages) {
                        fail("Subscriber " + subscriber.getName() + " did not receive defined count of messages.");
                    }
                }
            }

        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            container(1).stop();
        }
    }

}
