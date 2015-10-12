package org.jboss.qa.artemis.test.protocol;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.mdb.RemoteMdbFromQueueToQueue;
import org.jboss.qa.hornetq.tools.ActiveMQAdminOperationsEAP7;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by okalman on 8/27/15.
 */
public abstract class ProtocolCompatibilityTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ProtocolCompatibilityTestCase.class);

    final String IN_QUEUE_NAME = "InQueue";
    final String IN_QUEUE_ADDRESS = "jms.queue." + IN_QUEUE_NAME;
    final String IN_QUEUE_JNDI_NAME = "jms/queue/"+IN_QUEUE_NAME;
    final String TEST = "test";
    final String OUT_QUEUE_NAME = "OutQueue";
    final String OUT_QUEUE_ADDRESS = "jms.queue." + OUT_QUEUE_NAME;
    final String OUT_QUEUE_JNDI_NAME = "jms/queue/"+ OUT_QUEUE_NAME;
    final int MSG_APPENDS = 10 * 1024;
    final String CF_NAME="activemq-ra";
    final String CONNECTOR_NAME = "myremote-connector";
    final String ACCEPTOR_NAME = "myremote-acceptor";
    final int REMOTE_PORT_BROKER = 62616;
    final int REMOTE_PORT_EAP = 63616;
    final String BROKER_REMOTE_ADDRESS = "localhost";

    final JavaArchive mdb = createDeploymentMdb();

    @Before
    public void cleanUpBroker() throws Exception{
        String brokerHome = System.getenv("ARTEMIS_INSTALLED");
        if(brokerHome != null && brokerHome.length()>0){
            File data = new File (brokerHome + File.separator + "data");
            FileUtils.cleanDirectory(data);
        }

    }

    @After
    @Before
    public void stopAllServers() {
        container(1).stop();
    }

    protected void prepareServerForStandaloneBroker(){
        container(1).start();
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.createOutBoundSocketBinding("remote-broker-binding", BROKER_REMOTE_ADDRESS, REMOTE_PORT_BROKER);
        jmsAdminOperations.createRemoteConnector("remote-broker-connector", "remote-broker-binding", null);
        Map<String,String>params = new HashMap<String, String>();
        params.put("java.naming.factory.initial", "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
        params.put("java.naming.provider.url", "tcp://127.0.0.1:62616");
        jmsAdminOperations.addExternalContext("java:global/remoteContext", "javax.naming.InitialContext", "org.apache.activemq.artemis", "external-context",params );
        container(1).stop();
        container(1).start();
        jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.createPooledConnectionFactory("CF", "java:/jms/CF", "remote-broker-connector");
        jmsAdminOperations.setDefaultResourceAdapter("CF");

        container(1).stop();
    }


    protected  void prepareServer(){
        container(1).start();
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.createQueue(IN_QUEUE_NAME,IN_QUEUE_JNDI_NAME);
        jmsAdminOperations.createQueue(OUT_QUEUE_NAME, OUT_QUEUE_JNDI_NAME);
        Map<String, String> params = new HashMap<String, String>();
        jmsAdminOperations.createSocketBinding("myRemote", REMOTE_PORT_EAP);
        ((ActiveMQAdminOperationsEAP7)jmsAdminOperations).createRemoteAcceptor("default","myAcceptor", "myRemote",params, "AMQP, STOMP");
        container(1).stop();
    }

    protected static JavaArchive createDeploymentMdb() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb.jar");
        mdbJar.addClass(RemoteMdbFromQueueToQueue.class);
        mdbJar.addAsManifestResource(new StringAsset(getManifest()), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        File target = new File("/tmp/" + "mdb.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    private static String getManifest() {
        return "Dependencies: org.jboss.remote-naming, org.apache.activemq.artemis, org.apache.activemq.artemis.ra";
    }
}
