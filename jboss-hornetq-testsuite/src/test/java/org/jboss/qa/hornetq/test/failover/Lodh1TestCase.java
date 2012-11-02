package org.jboss.qa.hornetq.test.failover;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class Lodh1TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh1TestCase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000;

    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueue = "jms/queue/" + inQueueName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueue = "jms/queue/" + outQueueName;

    @Deployment(managed = false, testable = false, name = "mdb1")
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh1Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh1");

        mdbJar.addClass(LocalMdbFromQueue.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");

        StringBuffer ejbXml = new StringBuffer();

        ejbXml.append("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n");
        ejbXml.append("<jboss:ejb-jar xmlns:jboss=\"http://www.jboss.com/xml/ns/javaee\"\n");
        ejbXml.append("xmlns=\"http://java.sun.com/xml/ns/javaee\"\n");
        ejbXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        ejbXml.append("xmlns:c=\"urn:clustering:1.0\"\n");
        ejbXml.append("xsi:schemaLocation=\"http://www.jboss.com/xml/ns/javaee http://www.jboss.org/j2ee/schema/jboss-ejb3-2_0.xsd http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/ejb-jar_3_1.xsd\"\n");
        ejbXml.append("version=\"3.1\"\n");
        ejbXml.append("impl-version=\"2.0\">\n");
        ejbXml.append("<enterprise-beans>\n");
        ejbXml.append("<message-driven>\n");
        ejbXml.append("<ejb-name>mdb-lodh1</ejb-name>\n");
        ejbXml.append("<ejb-class>org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue</ejb-class>\n");
        ejbXml.append("<activation-config>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destination</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>" + inQueue + "</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destinationType</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>javax.jms.Queue</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("</activation-config>\n");
        ejbXml.append("<resource-ref>\n");
        ejbXml.append("<res-ref-name>queue/OutQueue</res-ref-name>\n");
        ejbXml.append("<jndi-name>" + outQueue + "</jndi-name>\n");
        ejbXml.append("<res-type>javax.jms.Queue</res-type>\n");
        ejbXml.append("<res-auth>Container</res-auth>\n");
        ejbXml.append("</resource-ref>\n");
        ejbXml.append("</message-driven>\n");
        ejbXml.append("</enterprise-beans>\n");
        ejbXml.append("</jboss:ejb-jar>\n");
        ejbXml.append("\n");

        mdbJar.addAsManifestResource(new StringAsset(ejbXml.toString()), "jboss-ejb3.xml");

        logger.info(ejbXml);
        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/mdb.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testKill() throws Exception {
        testLodh(false);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testShutDown() throws Exception {
        testLodh(true);
    }

    /**
     * @throws Exception
     */
    public void testLodh(boolean shutdown) throws Exception {

        // we use only the first server
        prepareServer();

        controller.start(CONTAINER1);

        SoakProducerClientAck producer1 = new SoakProducerClientAck(CONTAINER1_IP, 4447, inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 150);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
//        producer1.setMessageBuilder(new TextMessageBuilder(10000));

        logger.info("Start producer.");
        producer1.start();
        producer1.join();

        deployer.deploy("mdb1");

        List<String> killSequence = new ArrayList<String>();

        for (int i = 0; i < 5; i++) {
            killSequence.add(CONTAINER1);
        }

        executeNodeFaillSequence(killSequence, 60000, shutdown);

        logger.info("Start receiver.");
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(CONTAINER1_IP, 4447, outQueue, 600000, 10, 10);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producer1.getCounter());
        logger.info("Number of received messages: " + receiver1.getCount());

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getCounter(),
                receiver1.getCount());
        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);

        List<String> lostMessages = checkLostMessages(producer1.getListOfSentMessages(), receiver1.getListOfReceivedMessages());
        Assert.assertEquals("There are lost messages. Check logs for details.", 0, lostMessages.size());
        for (String dupId : lostMessages) {
            logger.info("Lost message - _HQ_DUPL_ID=" + dupId);
        }

        deployer.undeploy("mdb1");
        stopServer(CONTAINER1);

    }

    private List<String> checkLostMessages(List<String> listOfSentMessages, List<String> listOfReceivedMessages) {

        //get lost messages
        List<String> listOfLostMessages = new ArrayList<String>();

        for (String duplicateId : listOfSentMessages) {
            if (!listOfReceivedMessages.contains(duplicateId)) {
                listOfLostMessages.add(duplicateId);
            }
        }
        return listOfLostMessages;
    }

    /**
     * Executes kill sequence.
     *
     * @param failSequence     map Contanier -> ContainerIP
     * @param timeBetweenFails time between subsequent kills (in milliseconds)
     */
    private void executeNodeFaillSequence(List<String> failSequence, long timeBetweenFails, boolean shutdown) throws InterruptedException {

        if (shutdown) {
            for (String containerName : failSequence) {
                Thread.sleep(timeBetweenFails);
                logger.info("Shutdown server: " + containerName);
                controller.stop(containerName);
                Thread.sleep(3000);
                logger.info("Start server: " + containerName);
                controller.start(containerName);
                logger.info("Server: " + containerName + " -- STARTED");
            }
        } else {
            for (String containerName : failSequence) {
                Thread.sleep(timeBetweenFails);
                killServer(containerName);
                Thread.sleep(3000);
                controller.kill(containerName);
                logger.info("Start server: " + containerName);
                controller.start(containerName);
                logger.info("Server: " + containerName + " -- STARTED");
            }
        }
    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     *
     * @throws Exception
     */
    @Before
    @After
    public void stopAllServers() throws Exception {

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

        deleteFolder(new File(JOURNAL_DIRECTORY_A));

    }

    /**
     * Prepare server in simple topology.
     *
     * @throws Exception
     */
    public void prepareServer() throws Exception {
        prepareJmsServer(CONTAINER1, CONTAINER1_IP);
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param containerName  Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     */
    private void prepareJmsServer(String containerName, String bindingAddress) throws IOException {

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);
//        jmsAdminOperations.setInetAddress("public", bindingAddress);
//        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
//        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 512 * 1024, 0, 0, 50 *1024);

        try {
            jmsAdminOperations.removeQueue(inQueueName);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createQueue("default", inQueueName, inQueue, true);

        try {
            jmsAdminOperations.removeQueue(outQueueName);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createQueue("default", outQueueName, outQueue, true);
        jmsAdminOperations.close();

        controller.stop(containerName);

    }
}