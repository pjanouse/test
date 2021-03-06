package org.jboss.qa.artemis.test.messages;

import category.Functional;
import com.opencsv.CSVReader;
import org.apache.activemq.artemis.utils.IPV6Util;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients.PublisherAutoAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.resourcemonitor.FileMeasurement;
import org.jboss.qa.resourcemonitor.ResourceMonitor;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
@Category(Functional.class)
public class LargeMessageFileDescriptorsTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(LargeMessageFileDescriptorsTestCase.class);
    private final int NUMBER_OF_MESSAGES = 1000;

    /**
     * @tpTestDetails Single server with deployed queue is started, paging threshold is lowered.
     * Large Messages are send to queue. number of open file descriptors are monitored.
     * @tpProcedure <ul>
     * <li>Start server with queue and lowered paging threshold.</li>
     * <li>Send large messages to queue</li>
     * <li>Check open file descriptors difference</li>
     * </ul>
     * @tpPassCrit Not more than 2% of open file descriptors gain during test.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "1024")
    })
    public void openFileDescriptorsOnQueueTest() throws Exception {

        //ResourceMonitor can not measure open file descriptors on Windows currently. Once implemented, assume will be removed
        Assume.assumeTrue("Test currently runs only on Linux", System.getProperty("os.name").contains("Linux"));

        container(1).start();

        ResourceMonitor resourceMonitor = new ResourceMonitor.Builder()
                .setMeasurable(FileMeasurement.class, 5000)
                .host(IPV6Util.encloseHost(container(1).getHostname()))
                .port(container(1).getPort())
                .protocol(ResourceMonitor.Builder.JMX_URL_EAP7)
                .outFileNamingPattern("target/server")
                .keepCsv(true)
                .build();
        resourceMonitor.startMeasuring();

        ProducerAutoAck producer = new ProducerAutoAck(container(1), PrepareConstants.QUEUE_JNDI, NUMBER_OF_MESSAGES);
        producer.setMessageBuilder(new ClientMixMessageBuilder(150, 150));
        producer.start();
        producer.join();

        resourceMonitor.stopMeasuring();
        container(1).stop();

        List<Integer> values = readValues();
        Integer first = values.get(0);

        for (Integer value : values) {
            assertTrue("More than 2% file descriptor gain when large messages are processed by server", value.doubleValue() < first.doubleValue() * 1.02);
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "1024")
    })
    public void openFileDescriptorsOnTopicTest() throws Exception {

        // todo ResourceMonitor can not measure open file descriptors on Windows currently. Once implemented, assume will be removed
        Assume.assumeTrue("Test currently runs only on Linux", System.getProperty("os.name").contains("Linux"));

        container(1).start();

        ResourceMonitor resourceMonitor = new ResourceMonitor.Builder()
                .setMeasurable(FileMeasurement.class, 5000)
                .host(IPV6Util.encloseHost(container(1).getHostname()))
                .port(container(1).getPort())
                .protocol(ResourceMonitor.Builder.JMX_URL_EAP7)
                .outFileNamingPattern("target/server")
                .keepCsv(true)
                .build();
        resourceMonitor.startMeasuring();

        PublisherAutoAck producer = new PublisherAutoAck(container(1), PrepareConstants.TOPIC_JNDI, NUMBER_OF_MESSAGES, "client1");
        producer.setMessageBuilder(new ClientMixMessageBuilder(150, 150));

        SubscriberAutoAck fastConsumer = new SubscriberAutoAck(container(1),
                PrepareConstants.TOPIC_JNDI, "subscriber-1", "test-fast-subscriber");
        SubscriberAutoAck slowConsumer = new SubscriberAutoAck(container(1),
                PrepareConstants.TOPIC_JNDI, "subscriber-2", "test-slow-subscriber");
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
        container(1).stop();

        List<Integer> values = readValues();
        Integer first = values.get(0);

        for (Integer value : values) {
            assertTrue("There are more than 50 new file descriptors after test. It's potential leak. Number of file descriptor before test: " + first.doubleValue()
                    + ", Number of file descriptors after test: " + value.doubleValue(), value.doubleValue() - first.doubleValue() < 50);
        }

    }

    private List<Integer> readValues() throws Exception {
        List<Integer> values = new ArrayList<Integer>();
        CSVReader reader = new CSVReader(new FileReader("target/server_FileMeasurement.csv"));
        List<String[]> myEntries = reader.readAll();
        if (myEntries.size() < 2 || myEntries.get(0).length != 2) {
            fail("Incorrect csv");
        }

        for (int i = 1; i < myEntries.size(); i++) {
            values.add(Integer.valueOf(myEntries.get(i)[1]));
        }
        return values;
    }

}
