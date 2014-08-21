package org.jboss.qa.hornetq.test.cluster;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.qa.hornetq.PrintJournal;
import org.jboss.qa.hornetq.apps.clients.ProducerResp;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueToTempQueue;
import org.jboss.qa.hornetq.tools.HornetQAdminOperationsEAP6;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by okalman on 8/13/14.
 */
public class TempQueuesClusterTestCase extends ClusterTestCase {
    private static final Logger log = Logger.getLogger(TempQueuesClusterTestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 500;

    private static final String MDB_ON_QUEUE1_TEMP_QUEUE = "mdbQueue1witTempQueue";


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void TempQueuesClusterTestTempQueueonOtherNodes() {
        prepareServers(true);
        controller.start(CONTAINER2);
        controller.start(CONTAINER1);
        deployer.deploy(MDB_ON_QUEUE1_TEMP_QUEUE);
        try {
            int cont1Count = 0, cont2Count = 0;
            ProducerResp responsiveProducer = new ProducerResp(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
            JMSOperations jmsAdminOperationsContainer1 = getJMSOperations(CONTAINER1);
            JMSOperations jmsAdminOperationsContainer2 = getJMSOperations(CONTAINER2);
            responsiveProducer.start();
            // Wait fro creating connections and send few messages
            Thread.sleep(1000);
            cont1Count = jmsAdminOperationsContainer1.getNumberOfTempQueues();
            cont2Count = jmsAdminOperationsContainer2.getNumberOfTempQueues();
            responsiveProducer.join();
            Assert.assertEquals("Invalid number of temp queues on CONTAINER1", 1, cont1Count);
            Assert.assertEquals("Invalid number of temp queues on CONTAINER2", 0, cont2Count);

        } catch (Exception e) {
            log.error("Error occurred ", e);
        }

    }


    /**
     * TODO
     */

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void TempQueuesClusterTestPagingAfterFailOver() {
        prepareServers(true);
        controller.start(CONTAINER1);
        try {
            int counter = 0;
            ArrayList<File> pagingFilesPath = new ArrayList<File>();
            JMSOperations jmsAdminOperations = getJMSOperations(CONTAINER1);
            Context context = getContext(CONTAINER1);
            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryName());
            Connection connection = cf.createConnection();
            Session session = connection.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
            TemporaryQueue tempQueue = session.createTemporaryQueue();
            MessageProducer producer = session.createProducer(tempQueue);
            TextMessage message = session.createTextMessage("message");
            for (int i = 0; i < 10000; i++) {
                producer.send(message);
            }

            killServer(CONTAINER1);
            controller.start(CONTAINER1);

            File mainPagingDirectoryPath = new File(jmsAdminOperations.getPagingDirectoryPath());
            ArrayList<File> pagingDirectories = new ArrayList<File>(Arrays.asList(mainPagingDirectoryPath.listFiles()));
            for (File dir : pagingDirectories) {
                if (dir.isDirectory()) {
                    ArrayList<File> files = new ArrayList<File>(Arrays.asList(dir.listFiles()));
                    for (File f : files) {
                        if (f.isFile() && !f.getName().contains("address")) {
                            counter++;
                        }

                    }

                }

            }

            Assert.assertEquals("Too many paging files", 0, counter);


        } catch (Exception e) {
            log.error("Error occurred ", e);
        } finally {
            stopAllServers();
        }

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void TempQueuesClusterTestReadMessageFromDifferentConnection() {
        prepareServers(true);
        boolean failed = false;
        controller.start(CONTAINER1);
        try {
            Context context = getContext(CONTAINER1);
            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryName());
            Connection connection1 = cf.createConnection();
            Connection connection2 = cf.createConnection();
            Session session1 = connection1.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
            Session session2 = connection2.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
            TemporaryQueue tempQueue = session1.createTemporaryQueue();
            MessageProducer producer = session1.createProducer(tempQueue);
            producer.send(session1.createTextMessage("message"));
            MessageConsumer consumer = session2.createConsumer(tempQueue);
            consumer.receive(100);
        } catch (JMSException e) {
            failed = true;
        } catch (Exception e) {
            log.warn("Unexpected exception " + e);
        } finally {
            stopAllServers();
        }
        Assert.assertEquals("Sending message didn't failed", true, failed);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void TempQueuesClusterTestTemQueueMessageExpiration() {
        prepareServers(true);
        controller.start(CONTAINER1);
        deployer.deploy(MDB_ON_QUEUE1_TEMP_QUEUE);
        try {
            int cont1Count = 0, cont2Count = 0;
            ProducerResp responsiveProducer = new ProducerResp(CONTAINER1, getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueueJndiNameForMdb, 1, 300);
            responsiveProducer.start();
            responsiveProducer.join();

            Assert.assertEquals("Number of recieved messages don't match", 0, responsiveProducer.getRecievedCount());


        } catch (Exception e) {
            log.error("Error occurred ", e);
        } finally {
            stopAllServers();
        }
    }


    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue and sends reply back to sender via tempQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE1_TEMP_QUEUE)
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createDeploymentMdbOnQueue1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1witTempQueue.jar");
        mdbJar.addClass(LocalMdbFromQueueToTempQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbOnQueue1.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }
}
