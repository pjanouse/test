package org.jboss.qa.artemis.test.messages;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.HashUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.resourcemonitor.MemoryMeasurement;
import org.jboss.qa.resourcemonitor.ResourceMonitor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.jms.*;
import javax.naming.Context;
import java.io.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @tpChapter Functional testing
 * @tpSubChapter MESSAGE CONTENT - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/job/eap7-artemis-qe-internal-ts-huge-messages/
 * @tpTestCaseDetails Test basic send - receive with large sized messages
 * <p>
 * Created by mstyk on 6/28/16.
 */
public class HugeMessageTestCase extends HornetQTestCase {
    private static final Logger log = Logger.getLogger(HugeMessageTestCase.class);
    private final String inQueue = "InQueue";
    private final String inQueueJndiName = "jms/queue/" + inQueue;
    private final File sourceFile = new File("sourceFile");
    private final File receivedFile = new File("receivedFile");

    @After
    @Before
    public void deleteLargeFiles() {
        if (sourceFile.exists()) {
            sourceFile.delete();
        }
        if (receivedFile.exists()) {
            receivedFile.delete();
        }
    }

    /**
     * @tpTestDetails Server is started. Send one byte message with size of 1GB.
     * Receive this message.
     * @tpProcedure <ul>
     * <li>Start server</li>
     * <li>Connect to the server with the producer and send 1GB test messages to the
     * queue.</li>
     * <li>Connect to the server with consumer and receive all messages from the queue</li>
     * <li>Check that message was send are received correctly</li>
     * </ul>
     * @tpPassCrit Large message is correctly send and received
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare("OneNode")
    public void test1GbMessage() throws Exception {
        Container container = container(1);
        container.start();

        ResourceMonitor serverMeasurement = new ResourceMonitor.Builder()
                .host(container.getHostname())
                .port(container.getPort())
                .protocol(ResourceMonitor.Builder.JMX_URL_EAP7)
                .outFileNamingPattern(container.getName())
                .generateCharts()
                .setMeasurable(MemoryMeasurement.class, TimeUnit.SECONDS.toMillis(10))
                .build();
        serverMeasurement.startMeasuring();


        generateSourceFile(1024 * 1024 * 1024, sourceFile);

        StreamingProducer streamingProducer = new StreamingProducer(container, sourceFile);
        streamingProducer.run();

        StreamingReceiver streamingReceiver = new StreamingReceiver(container, receivedFile);
        streamingReceiver.run();

        serverMeasurement.stopMeasuring();

        String sourceHash = HashUtils.getMd5(sourceFile);
        String receivedHash = HashUtils.getMd5(receivedFile);
        log.info("Source file [" + sourceHash + "] Received file [" + receivedHash + "]");

        Assert.assertEquals("File hash should be equal", sourceHash, receivedHash);
        container.stop();
    }

    /**
     * @tpTestDetails 2 servers are started in cluster. Send one byte message with size of 1GB to queue on node1.
     * Receive this message from queue on node2.
     * @tpProcedure <ul>
     * <li>Start server</li>
     * <li>Connect to the node1 with the producer and send 1GB test messages to the
     * queue.</li>
     * <li>Connect to the node2 with consumer and receive all messages from the queue</li>
     * <li>Check that message was send are received correctly</li>
     * </ul>
     * @tpPassCrit Large message is correctly send and received
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare("TwoNodes")
    public void test1GbMessageCluster() throws Exception {

        int redistributionWaitTimeMinutes = 30;
        JMSTools jmsTools = new JMSTools();

        container(1).start();
        container(2).start();

        ResourceMonitor server1Measurement = new ResourceMonitor.Builder()
                .host(container(1).getHostname())
                .port(container(1).getPort())
                .protocol(ResourceMonitor.Builder.JMX_URL_EAP7)
                .outFileNamingPattern(container(1).getName() + "-cluster")
                .generateCharts()
                .setMeasurable(MemoryMeasurement.class, TimeUnit.SECONDS.toMillis(10))
                .build();
        server1Measurement.startMeasuring();

        ResourceMonitor server2Measurement = new ResourceMonitor.Builder()
                .host(container(2).getHostname())
                .port(container(2).getPort())
                .protocol(ResourceMonitor.Builder.JMX_URL_EAP7)
                .outFileNamingPattern(container(2).getName() + "-cluster")
                .generateCharts()
                .setMeasurable(MemoryMeasurement.class, TimeUnit.SECONDS.toMillis(10))
                .build();
        server2Measurement.startMeasuring();

        generateSourceFile(1024 * 1024 * 1024, sourceFile);

        StreamingProducer streamingProducer = new StreamingProducer(container(1), sourceFile);
        streamingProducer.run();

        StreamingReceiver streamingReceiver = new StreamingReceiver(container(2), receivedFile);
        streamingReceiver.connect();

        log.info("Waiting for large message to redistribute on node 2 (max " + redistributionWaitTimeMinutes + " minutes)");
        jmsTools.waitForMessages(inQueue, 1, TimeUnit.MINUTES.toMillis(redistributionWaitTimeMinutes), container(2));

        log.info("Starting receive. Max 10 minutes timeout.");

        streamingReceiver.setTimeout(TimeUnit.MINUTES.toMillis(30));
        streamingReceiver.run();

        server1Measurement.stopMeasuring();
        server2Measurement.stopMeasuring();

        Assert.assertEquals("File hash should be equal", HashUtils.getMd5(sourceFile), HashUtils.getMd5(receivedFile));

        container(1).stop();
        container(2).stop();
    }

    private File generateSourceFile(int bytes, File sourceFile) {
        try (OutputStream os = new FileOutputStream(sourceFile)) {
            Random random = new Random(System.currentTimeMillis());
            byte data[] = new byte[1024];
            while (bytes > 0) {
                bytes -= 1024;
                random.nextBytes(data);
                os.write(data);
            }
            os.flush();
        } catch (IOException e) {
            log.error(e);
        }
        return sourceFile;
    }

    private class StreamingProducer implements Runnable {

        Container targetContainer;
        File streamFile;

        public StreamingProducer(Container targetContainer, File streamFile) {
            this.targetContainer = targetContainer;
            this.streamFile = streamFile;
        }

        @Override
        public void run() {
            Connection connection = null;
            try (InputStream is = new FileInputStream(streamFile);
                 BufferedInputStream bufferedInput = new BufferedInputStream(is);) {
                Context context = targetContainer.getContext();
                ConnectionFactory cf = (ConnectionFactory) context.lookup(Constants.CONNECTION_FACTORY_JNDI_EAP7);
                connection = cf.createConnection();
                connection.start();

                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = (Destination) context.lookup(inQueueJndiName);

                MessageProducer producer = session.createProducer(destination);

                long startTime = System.currentTimeMillis();
                log.info("Sending of message started");
                BytesMessage message = session.createBytesMessage();
                message.setObjectProperty("JMS_AMQ_InputStream", bufferedInput);
                producer.send(message);
                log.info("Sending of message finished in " + TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime) + " seconds");

            } catch (Exception e) {
                log.error(e);
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (JMSException e1) {
                        log.error(e1);
                    }
                }
            }
        }

    }

    private class StreamingReceiver implements Runnable {

        private Container fromContainer;
        private File targetFile;
        private MessageConsumer consumer = null;
        private Connection connection = null;
        private long timeout = 10000;

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

        public StreamingReceiver(Container fromContainer, File targetFile) {
            this.fromContainer = fromContainer;
            this.targetFile = targetFile;
        }

        public void connect() {
            try {
                Context context = fromContainer.getContext();
                ConnectionFactory cf = (ConnectionFactory) context.lookup(Constants.CONNECTION_FACTORY_JNDI_EAP7);
                connection = cf.createConnection();
                connection.start();

                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = (Destination) context.lookup(inQueueJndiName);

                consumer = session.createConsumer(destination);
            } catch (Exception e) {
                log.error(e);
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (JMSException e1) {
                        log.error(e1);
                    }
                }
            }
        }

        @Override
        public void run() {
            if (connection == null || consumer == null) {
                connect();
            }

            try (OutputStream outputStream = new FileOutputStream(targetFile);
                 BufferedOutputStream bufferedOutput = new BufferedOutputStream(outputStream);) {

                long startTime = System.currentTimeMillis();
                log.info("Receiveing of message started");
                BytesMessage messageReceived = (BytesMessage) consumer.receive(timeout);

                // This will block until the entire content is saved on disk
                messageReceived.setObjectProperty("JMS_AMQ_SaveStream", bufferedOutput);
                log.info("Receiving of message finished in " + TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime) + " seconds");
            } catch (Exception e) {
                log.error(e.getMessage());
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (JMSException e1) {
                        log.error(e1);
                    }
                }
            }
        }

    }

}
