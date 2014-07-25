package org.jboss.qa.hornetq.test.security;


import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.jms.*;
import javax.naming.Context;

import org.hornetq.api.core.Message;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.remoting.impl.netty.TransportConstants;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;


/**
 * PKCS11 tests info - how to generate certificates:
 * # vytvorit file s novym heslem
 echo "pass123+" > ${WORKSPACE}/newpass.txt

 modutil -force -create -dbdir ${WORKSPACE}/fipsdb
 modutil -force -fips true -dbdir ${WORKSPACE}/fipsdb
 modutil -force -changepw "NSS FIPS 140-2 Certificate DB" -newpwfile ${WORKSPACE}/newpass.txt -dbdir ${WORKSPACE}/fipsdb

 # vytvorit noise.txt
 echo "dsadasdasdasdadasdasdasdasdsadfwerwerjfdksdjfksdlfhjsdk" > ${WORKSPACE}/noise.txt

 certutil -S -k rsa -n jbossweb  -t "u,u,u" -x -s "CN=localhost, OU=MYOU, O=MYORG, L=MYCITY, ST=MYSTATE, C=MY" -d ${WORKSPACE}/fipsdb -f ${WORKSPACE}/newpass.txt -z ${WORKSPACE}/noise.txt
 certutil -L -d ${WORKSPACE}/fipsdb -n jbossweb -a > ${WORKSPACE}/cacert.asc

 * IMPORTANT:
 * SunPKCS11 is not supported on 64-bit Windows platforms. [1]

 [1] http://docs.oracle.com/javase/7/docs/technotes/guides/security/p11guide.html#Requirements
 *
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 * @author Miroslav Novak mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class SslAuthenticationTestCase extends SecurityTestBase {

    private static final String TEST_USER = "user";

    private static final String TEST_USER_PASSWORD = "user.456";

    private static final String QUEUE_NAME = "test.queue";

    private static final String QUEUE_JNDI_ADDRESS = "jms/test/queue";

    private static final String TEST_MESSAGE_BODY = "test text";

    private static final long CONSUMER_TIMEOUT = 10000;

    private static final String PKCS11_DB_DIRECTORY = "fipsdb";

    private static final String PKCS11_CONFIG_FILE_ORIGINAL = "pkcs11.cfg";

    private static final String PKCS11_CONFIG_FILE_MODIFIED = "pkcs11-modified.cfg";

    private static final String TRUSTSTORE_PROVIDER_PROP_NAME = "trust-store-provider";

    private static final String KEYSTORE_PROVIDER_PROP_NAME = "key-store-provider";


    @Before
    public void stopServerBeforeReconfiguration() {
        this.controller.stop(CONTAINER1);
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testOneWaySslOverCore() throws Exception {
        this.controller.start(CONTAINER1);
        JMSOperations ops = this.prepareServer();
        this.createOneWaySslAcceptor(ops);
        this.prepareServerSideKeystores();
        ops.close();
        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        ServerLocator locator = null;
        try {
            Map<String, Object> props = new HashMap<String, Object>();
            props.put(TransportConstants.HOST_PROP_NAME, getHostname(CONTAINER1));
            props.put(TransportConstants.PORT_PROP_NAME, getHornetqPort(CONTAINER1));
            props.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
            props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, TRUST_STORE_PATH);
            props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
            TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                    props);

            locator = HornetQClient.createServerLocatorWithoutHA(config);
            ClientSessionFactory sf = locator.createSessionFactory();
            ClientSession session = sf.createSession(TEST_USER, TEST_USER_PASSWORD, false, true, true, false,
                    locator.getAckBatchSize());
            session.createTemporaryQueue(QUEUE_JNDI_ADDRESS, QUEUE_NAME);

            ClientProducer producer = session.createProducer(QUEUE_JNDI_ADDRESS);
            ClientMessage msg = session.createMessage(Message.TEXT_TYPE, false, 0, System.currentTimeMillis(), (byte) 4);
            msg.getBodyBuffer().writeString(TEST_MESSAGE_BODY);
            producer.send(msg);

            ClientConsumer consumer = session.createConsumer(QUEUE_NAME);
            session.start();
            Message received = consumer.receive(CONSUMER_TIMEOUT);

            assertNotNull("Cannot consume test message", received);
            assertEquals("Sent and received messages have different body",
                    TEST_MESSAGE_BODY, received.getBodyBuffer().readString());

            session.stop();
            producer.close();
            session.close();
            sf.close();
        } finally {
            if (locator != null) {
                locator.close();
            }
        }
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testOneWaySslOverJms() throws Exception {
        this.controller.start(CONTAINER1);
        JMSOperations ops = this.prepareServer();
        this.createOneWaySslAcceptor(ops);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);
        this.prepareServerSideKeystores();
        ops.close();
        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(TransportConstants.HOST_PROP_NAME, getHostname(CONTAINER1));
        props.put(TransportConstants.PORT_PROP_NAME, getHornetqPort(CONTAINER1));
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, TRUST_STORE_PATH);
        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
        TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                props);

        HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, config);
        Connection connection = cf.createConnection(TEST_USER, TEST_USER_PASSWORD);
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();
        cf.close();
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testTwoWaySslOverCore() throws Exception {
        this.controller.start(CONTAINER1);
        JMSOperations ops = this.prepareServer();
        this.createTwoWaySslAcceptor(ops);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);
        this.prepareServerSideKeystores();
        ops.close();
        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(TransportConstants.HOST_PROP_NAME, getHostname(CONTAINER1));
        props.put(TransportConstants.PORT_PROP_NAME, getHornetqPort(CONTAINER1));
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, TRUST_STORE_PATH);
        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
        props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, KEY_STORE_PATH);
        props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, KEY_STORE_PASSWORD);
        TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                props);

        HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, config);
        Connection connection = cf.createConnection(TEST_USER, TEST_USER_PASSWORD);
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();
        cf.close();

    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testTwoWaySslOverJms() throws Exception {
        this.controller.start(CONTAINER1);
        JMSOperations ops = this.prepareServer();
        this.createTwoWaySslAcceptor(ops);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);
        this.prepareServerSideKeystores();
        ops.close();
        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(TransportConstants.HOST_PROP_NAME, getHostname(CONTAINER1));
        props.put(TransportConstants.PORT_PROP_NAME, getHornetqPort(CONTAINER1));
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, TRUST_STORE_PATH);
        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
        props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, KEY_STORE_PATH);
        props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, KEY_STORE_PASSWORD);
        TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                props);

        HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, config);
        Connection connection = cf.createConnection(TEST_USER, TEST_USER_PASSWORD);
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();
        cf.close();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTwoWaySslOverJmsWithPkcs11() throws Exception {

        prepareSeverWithPkcs11(CONTAINER1);

        this.controller.start(CONTAINER1);
        Context context = getContext(getHostname(CONTAINER1),getJNDIPort(CONTAINER1));
        ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryName());
        Connection connection = cf.createConnection(TEST_USER, TEST_USER_PASSWORD);
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();

        stopServer(CONTAINER1);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTwoWaySslOverJmsWithPkcs11CfCreatedByClient() throws Exception {

        prepareSeverWithPkcs11(CONTAINER1);

        this.controller.start(CONTAINER1);

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "cert8.db").getAbsolutePath());
        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "key3.db").getAbsolutePath());
        props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        props.put(TRUSTSTORE_PROVIDER_PROP_NAME,"PKCS11");
        props.put(KEYSTORE_PROVIDER_PROP_NAME,"PKCS11");
        props.put(TransportConstants.HOST_PROP_NAME, getHostname(CONTAINER1));
        props.put(TransportConstants.PORT_PROP_NAME, getHornetqPort(CONTAINER1));
        TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                props);

        HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, config);

        Connection connection = cf.createConnection(TEST_USER, TEST_USER_PASSWORD);
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();
        cf.close();

        stopServer(CONTAINER1);

    }

    private void prepareSeverWithPkcs11(String containerName) throws Exception {

        installSecurityExtension(CONTAINER1);

        this.controller.start(CONTAINER1);
        JMSOperations ops = this.prepareServer();

        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);

        // enable logging
        ops.addLoggerCategory("org.jboss.security", "TRACE");

        // enable SunPKCS11 security provider EAP 6 server
        ops.addExtension("org.jboss.as.security.providers");
        ops.addSubsystem("security-providers");
        Map<String,String> attributes = new HashMap<String, String>();
        attributes.put("nssLibraryDirectory", System.getProperty("sun.arch.data.model").equals("64") ?
                "/usr/lib64": "/usr/lib");
        attributes.put("nssSecmodDirectory", new File(TEST_KEYSTORES_DIRECTORY, "fipsdb").getAbsolutePath());
        attributes.put("nssModule", "fips");
        ops.addSecurityProvider("sunpkcs11", "nss-fips", attributes);

        // enable it for this arquillian test
//        name=PKCS11
//        nssLibraryDirectory=/usr/lib64
//        nssSecmodDirectory=/home/mnovak/tmp/pkcs11/fipsdb
//        nssModule=fips
        copyFile(new File(TEST_KEYSTORES_DIRECTORY, PKCS11_CONFIG_FILE_ORIGINAL), new File(TEST_KEYSTORES_DIRECTORY, PKCS11_CONFIG_FILE_MODIFIED));
        File pkcs11ConfigFile = new File(TEST_KEYSTORES_DIRECTORY, PKCS11_CONFIG_FILE_MODIFIED);
        replaceStringInFile(pkcs11ConfigFile, "nssLibraryDirectory=", System.getProperty("sun.arch.data.model").equals("64") ?
                "nssLibraryDirectory=/usr/lib64": "nssLibraryDirectory=/usr/lib");
        replaceStringInFile(pkcs11ConfigFile, "nssSecmodDirectory=",
                "nssSecmodDirectory=" + new File(TEST_KEYSTORES_DIRECTORY, "fipsdb").getAbsolutePath());
        PKCS11Utils.registerProvider(pkcs11ConfigFile.getAbsolutePath());


        String acceptorConnectorName = "netty";
        String messagingGroupSocketBindingName = "messaging";

        ops.removeRemoteConnector(acceptorConnectorName);
        ops.removeRemoteAcceptor(acceptorConnectorName);

        // create connector and acceptor with ssl certificates
        Map<String, String> acceptorProps = new HashMap<String, String>();
        acceptorProps.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        acceptorProps.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "cert8.db").getAbsolutePath());
        acceptorProps.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        acceptorProps.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "key3.db").getAbsolutePath());
        acceptorProps.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        acceptorProps.put(TRUSTSTORE_PROVIDER_PROP_NAME,"PKCS11");
        acceptorProps.put(KEYSTORE_PROVIDER_PROP_NAME,"PKCS11");
        acceptorProps.put("need-client-auth", "true");
        ops.createRemoteAcceptor(acceptorConnectorName, messagingGroupSocketBindingName, acceptorProps);

        Map<String, String> connectorProps = new HashMap<String, String>();
        connectorProps.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        connectorProps.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "cert8.db").getAbsolutePath());
        connectorProps.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        connectorProps.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "key3.db").getAbsolutePath());
        connectorProps.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        connectorProps.put(TRUSTSTORE_PROVIDER_PROP_NAME,"PKCS11");
        connectorProps.put(KEYSTORE_PROVIDER_PROP_NAME,"PKCS11");
        ops.createRemoteConnector(acceptorConnectorName, messagingGroupSocketBindingName, connectorProps);

        ops.setConnectorOnConnectionFactory("RemoteConnectionFactory", acceptorConnectorName);
        ops.setSecurityEnabled(true);

        ops.close();

        stopServer(CONTAINER1);
    }

    /**
     * Creates org.jboss.as.security.providers module so PKCS11 provider from can be loaded
     */
    private void installSecurityExtension(String containerName) throws Exception {

        final String securityJarFileName = "security-providers.jar";

        // create modules/system/layers/base/org/jboss/as/security/providers/main
        File moduleDir = new File(getJbossHome(containerName),
                "modules" + File.separator + "system" + File.separator + "layers" + File.separator + "base"
                + File.separator + "org" + File.separator + "jboss" + File.separator + "as"
                + File.separator + "security" + File.separator + "providers" + File.separator + "main");
        if (moduleDir.exists()) {
            moduleDir.delete();
        }
        moduleDir.mkdirs();

        // create module.xml
        File moduleXml = new File(moduleDir, "module.xml");
        if (moduleXml.exists()) {
            moduleDir.delete();
        }
        moduleXml.createNewFile();
        PrintWriter writer = new PrintWriter(moduleXml, "UTF-8");
        writer.println("<module xmlns=\"urn:jboss:module:1.0\" name=\"org.jboss.as.security.providers\">");
        writer.println("<resources>");
        writer.println("<resource-root path=\"" + securityJarFileName + "\"/>");
        writer.println("</resources>");
        writer.println("<dependencies>");
        writer.println("<module name=\"javax.api\"/>");
        writer.println("<module name=\"org.jboss.staxmapper\"/>");
        writer.println("<module name=\"org.jboss.as.controller\"/>");
        writer.println("<module name=\"org.jboss.as.server\"/>");
        writer.println("<module name=\"org.jboss.modules\"/>");
        writer.println("<module name=\"org.jboss.msc\"/>");
        writer.println("<module name=\"org.jboss.logging\"/>");
        writer.println("<module name=\"org.jboss.vfs\"/>");
        writer.println("<module name=\"sun.jdk\"/>");
        writer.println("</dependencies>");
        writer.println("</module>");
        writer.close();

        // copy there security-providers-1.0-SNAPSHOT.jar from target dir
        File secJar = new File("target", securityJarFileName);
        if (!secJar.exists())    {
            throw new Exception("File: " + secJar.getAbsolutePath() + " does not exists.");
        }
        File targetforSecjar = new File(moduleDir, securityJarFileName);
        if (targetforSecjar.exists())   {
            targetforSecjar.delete();
        }
        targetforSecjar.createNewFile();
        copyFile(secJar, targetforSecjar);

        // patch $JBOSS_HOME/modules/sun/jdk/main/module.xml by sed -i 's#\(<path name="sun/security/provider"/>\)#\1<path name="sun/security/pkcs11"/>#'
        File jdkModuleXml = new File(getJbossHome(containerName), "modules" + File.separator + "system"
                + File.separator + "layers" + File.separator + "base" + File.separator + "sun"
                + File.separator + "jdk" + File.separator + "main" + File.separator + "module.xml");
        if (!jdkModuleXml.exists())  {
            throw new Exception("File: " + jdkModuleXml.getAbsolutePath() + " does not exists");
        }

        String original = "<path name=\"sun/security/provider\"/>";
        String replacement = "<path name=\"sun/security/pkcs11\"/>";
        replaceStringInFile(jdkModuleXml, original, replacement);

    }

    private static void replaceStringInFile(File file, String original, String replacement) throws IOException {

        // we need to store all the lines
        List<String> lines = new ArrayList<String>();

        // first, read the file and store the changes
        BufferedReader in = new BufferedReader(new FileReader(file));
        String line = in.readLine();
        while (line != null) {
            if (line.contains(original)) {
                line = line.replaceAll(original, replacement);
            }
            lines.add(line);
            line = in.readLine();
        }
        in.close();

        // now, write the file again with the changes
        PrintWriter out = new PrintWriter(file);
        for (String l : lines)
            out.println(l);
        out.close();

    }


    private JMSOperations prepareServer() throws IOException {
        JMSOperations ops = this.getJMSOperations();
        ops.setPersistenceEnabled(true);

        AddressSecuritySettings.forDefaultContainer(this)
                .forAddress("jms.queue.test.#")
                .giveUserAllPermissions(TEST_USER)
                .create();

        UsersSettings.forDefaultEapServer()
                .withUser(TEST_USER, TEST_USER_PASSWORD, TEST_USER)
                .create();

        return ops;
    }

}
