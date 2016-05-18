package org.jboss.qa.artemis.test.messages;

import com.opencsv.CSVReader;
import junit.framework.Assert;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients.PublisherAutoAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.resourcemonitor.FileMeasurement;
import org.jboss.qa.resourcemonitor.ResourceMonitor;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * @author mstyk@redhat.com
 * @tpChapter Functional testing
 * @tpSubChapter PAGING - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpTestCaseDetails This test case monitors open file descriptors raise during large message handling on server.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class LargeMessageFileDescriptorsTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(LargeMessageFileDescriptorsTestCase.class);
    private final String QUEUE_NAME = "testQueue";
    private final String QUEUE_JNDI_NAME = "jms/queue/" + QUEUE_NAME;
    private final String TOPIC_NAME = "testTopic";
    private final String TOPIC_JNDI_NAME = "jms/topic/" + TOPIC_NAME;
    private final int NUMBER_OF_MESSAGES = 1000;

    /**
     * @tpTestDetails Single server with deployed queue is started, paging threshold is lowered.
     * Large Messages are send to queue. number of open file descriptors are monitored.
     * @tpProcedure <ul>
     * <li>Start server with queue and lowered paging threshold.</li>
     * <li>Send large messages to queue</li>
     * <li>Check open file descriptors difference</li>
     * </ul>
     * @tpPassCrit Not more than 1percent of open file descriptors gain during test.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void openFileDescriptorsOnQueueTest() throws Exception {

        container(1).start();
        prepareServer(container(1));

        ResourceMonitor resourceMonitor = new ResourceMonitor.Builder()
                .setMeasurables(FileMeasurement.class)
                .host(container(1).getHostname())
                .port(container(1).getPort())
                .protocol(ResourceMonitor.Builder.JMX_URL_EAP7)
                .processId(container(1).getProcessId())
                .measurePeriod(5000)
                .outFileNamingPattern("server")
                .keepCsv(true)
                .build();
        resourceMonitor.start();

        ProducerAutoAck producer = new ProducerAutoAck(container(1), QUEUE_JNDI_NAME, NUMBER_OF_MESSAGES);
        producer.setMessageBuilder(new ClientMixMessageBuilder(150, 150));
        producer.start();
        producer.join();

        resourceMonitor.stopMeasuring();
        resourceMonitor.join();
        container(1).stop();

        List<Integer> values = readValues();
        Integer first = values.get(0);

        for (Integer value : values) {
            assertTrue("More than 1% file descriptor gain when large messages are processed by server", value.doubleValue() < first.doubleValue() * 1.01);
        }

    }

    /**
     * @tpTestDetails Single server with deployed topic is started, paging threshold is lowered.
     * Large Messages are send to topic. Slow and fast consumer consume messages from topic.
     * Number of open file descriptors are monitored.
     * @tpProcedure <ul>
     * <li>Start server with queue and lowered paging threshold.</li>
     * <li>Send large messages to queue</li>
     * <li>Check open file descriptors difference</li>
     * </ul>
     * @tpPassCrit Not more than 1percent of open file descriptors gain during test.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void openFileDescriptorsOnTopicTest() throws Exception {

        container(1).start();
        prepareServer(container(1));

        ResourceMonitor resourceMonitor = new ResourceMonitor.Builder()
                .setMeasurables(FileMeasurement.class)
                .host(container(1).getHostname())
                .port(container(1).getPort())
                .protocol(ResourceMonitor.Builder.JMX_URL_EAP7)
                .processId(container(1).getProcessId())
                .measurePeriod(5000)
                .outFileNamingPattern("server")
                .keepCsv(true)
                .build();
        resourceMonitor.start();

        PublisherAutoAck producer = new PublisherAutoAck(container(1), TOPIC_JNDI_NAME, NUMBER_OF_MESSAGES, "client1");
        producer.setMessageBuilder(new ClientMixMessageBuilder(150, 150));

        SubscriberAutoAck fastConsumer = new SubscriberAutoAck(container(1),
                TOPIC_JNDI_NAME, "subscriber-1", "test-fast-subscriber");
        SubscriberAutoAck slowConsumer = new SubscriberAutoAck(container(1),
                TOPIC_JNDI_NAME, "subscriber-2", "test-slow-subscriber");
        slowConsumer.setTimeout(500); // slow consumer reads only 2 messages per second
        slowConsumer.setMaxRetries(1);

        fastConsumer.subscribe();
        slowConsumer.subscribe();
        fastConsumer.start();
        slowConsumer.start();
        producer.start();
        producer.join();

        slowConsumer.setTimeout(0);
        fastConsumer.setTimeout(0);
        slowConsumer.join();
        fastConsumer.join();

        resourceMonitor.stopMeasuring();
        resourceMonitor.join();
        container(1).stop();

        List<Integer> values = readValues();
        Integer first = values.get(0);

        for (Integer value : values) {
            assertTrue("More than 1% file descriptor gain when large messages are processed by server", value.doubleValue() < first.doubleValue() * 1.01);
        }

    }

    private void prepareServer(Container container) {
        JMSOperations jmsOperations = container.getJmsOperations();
        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
        jmsOperations.removeAddressSettings("#");
        jmsOperations.addAddressSettings("#", "PAGE", 2 * 1024, 0, 0, 1 * 1024);
        jmsOperations.close();
    }

    private List<Integer> readValues() throws Exception {
        List<Integer> values = new ArrayList<Integer>();
        CSVReader reader = new CSVReader(new FileReader("server.csv"));
        List<String[]> myEntries = reader.readAll();
        if (myEntries.size() < 2 || myEntries.get(0).length != 2) {
            Assert.fail("Incorrect csv");
        }

        for (int i = 1; i < myEntries.size(); i++) {
            values.add(Integer.valueOf(myEntries.get(i)[1]));
        }
        return values;
    }

}
