package org.jboss.qa.hornetq.test.annotation;

import java.io.File;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueAnnotated;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 *
 * @author mstyk
 */


@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class AnnotationsTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(AnnotationsTestCase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000;

    private final Archive mdbDeployment = createDeployment();

    // queue to send messages in
    static String inQueueName = "InQueue";
    static String inQueue = "jms/queue/" + inQueueName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueue = "jms/queue/" + outQueueName;
    
    public JavaArchive createDeployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh1");

        mdbJar.addClass(LocalMdbFromQueueAnnotated.class);

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
        ejbXml.append("<ejb-class>org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueAnnotated</ejb-class>\n");
        ejbXml.append("<activation-config>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destination</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>").append(inQueue).append("</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destinationType</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>javax.jms.Queue</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("</activation-config>\n");
        ejbXml.append("<resource-ref>\n");
        ejbXml.append("<res-ref-name>queue/OutQueue1</res-ref-name>\n");
        ejbXml.append("<jndi-name>").append(outQueue).append("</jndi-name>\n");
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
         // Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/mdb.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest 
    @CleanUpBeforeTest
    public void testJcaSmallMessages() throws Exception {
        MessageBuilder messageBuilder = new TextMessageBuilder(10);
        
        // we use only the first server
        prepareServer(container(1));

        container(1).start();

        SoakProducerClientAck producer1 = new SoakProducerClientAck(container(1), inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setTimeout(0);

        logger.info("Start producer.");
        producer1.start();
        producer1.join();

        container(1).deploy(mdbDeployment);

        logger.info("Start receiver.");
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(container(1), outQueue, 6000, 10, 10);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producer1.getCounter());
        logger.info("Number of received messages: " + receiver1.getCount());

//        List<String> lostMessages = checkLostMessages(producer1.getListOfSentMessages(), receiver1.getListOfReceivedMessages());
//        Assert.assertEquals("There are lost messages. Check logs for details.", 0, lostMessages.size());
//        for (String dupId : lostMessages) {
//            logger.info("Lost message - _HQ_DUPL_ID=" + dupId);
//        }

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getCounter(),
                receiver1.getCount());
        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);


        container(1).undeploy("mdbDeployment");
        container(1).stop();

    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
    @Before
    @After
    public void stopAllServers() {
        container(1).stop();
        container(2).stop();
    }


    private void prepareServer(Container container) {
        String connectionFactoryName = Constants.RESOURCE_ADAPTER_NAME_EAP7;
        prepareJmsServer(container, connectionFactoryName);
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServer(Container container, String connectionFactoryName) {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
      /*  jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 512 * 1024, 0, 0, 50 * 1024);
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.removeBroadcastGroup("bg-group1");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");*/
        jmsAdminOperations.setMinPoolSizeOnPooledConnectionFactory(connectionFactoryName, 10);
        jmsAdminOperations.setMaxPoolSizeOnPooledConnectionFactory(connectionFactoryName, 20);

        try {
            jmsAdminOperations.removeQueue(inQueueName);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createQueue("default", inQueueName, inQueue, true);

        jmsAdminOperations.close();
        container.stop();
    }
}

