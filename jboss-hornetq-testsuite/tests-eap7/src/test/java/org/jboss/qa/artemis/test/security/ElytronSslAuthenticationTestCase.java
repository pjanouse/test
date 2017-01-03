package org.jboss.qa.artemis.test.security;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ServerPathUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.extras.creaper.commands.elytron.CreateServerSSLContext;
import org.wildfly.extras.creaper.core.ManagementClient;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;
import org.wildfly.extras.creaper.core.online.OnlineOptions;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * PKCS11 tests info - how to generate certificates:
 * # vytvorit file s novym heslem
 * echo "pass123+" > ${WORKSPACE}/newpass.txt
 * <p/>
 * modutil -force -create -dbdir ${WORKSPACE}/fipsdb
 * modutil -force -fips true -dbdir ${WORKSPACE}/fipsdb
 * modutil -force -changepw "NSS FIPS 140-2 Certificate DB" -newpwfile ${WORKSPACE}/newpass.txt -dbdir ${WORKSPACE}/fipsdb
 * <p/>
 * # vytvorit noise.txt
 * echo "dsadasdasdasdadasdasdasdasdsadfwerwerjfdksdjfksdlfhjsdk" > ${WORKSPACE}/noise.txt
 * <p/>
 * certutil -S -k rsa -n jbossweb  -t "u,u,u" -x -s "CN=localhost, OU=MYOU, O=MYORG, L=MYCITY, ST=MYSTATE, C=MY" -d ${WORKSPACE}/fipsdb -f ${WORKSPACE}/newpass.txt -z ${WORKSPACE}/noise.txt
 * certutil -L -d ${WORKSPACE}/fipsdb -n jbossweb -a > ${WORKSPACE}/cacert.asc
 * <p/>
 * IMPORTANT:
 * SunPKCS11 is not supported on 64-bit Windows platforms. [1]
 * <p/>
 * [1] http://docs.oracle.com/javase/7/docs/technotes/guides/security/p11guide.html#Requirements
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 * @author Miroslav Novak mnovak@redhat.com
 * @tpChapter Security testing
 * @tpSubChapter SSL AUTHENTICATION
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails Goal of the test cases is testing if the standalone JMS
 * client can connect to the EAP server using a connection over SSL.
 */
@RunWith(Arquillian.class)
public class ElytronSslAuthenticationTestCase extends SecurityTestBase {

    private static final Logger logger = Logger.getLogger(ElytronSslAuthenticationTestCase.class);

    private static final String QUEUE_NAME = "test.queue";

    private static final String QUEUE_JNDI_ADDRESS = "jms/test/queue";

    public enum SSL_TYPE {ONE_WAY, TWO_WAY}

    @Before
    public void cleanUpBeforeTest() {
        System.clearProperty("javax.net.ssl.keyStore");
        System.clearProperty("javax.net.ssl.keyStorePassword");
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
        container(1).stop();
    }

    @After
    public void stopAllServers() {
        container(1).stop();
    }

// todo this is one way ssl, only server authenticates to client, needs to provide correct prepare server (just keystore) method and just client trustore on client

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testHttpsConnectionServerAuth() throws Exception {
        testHttpsConnection(SSL_TYPE.ONE_WAY);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testHttpsConnectionBothAuth() throws Exception {
        testHttpsConnection(SSL_TYPE.TWO_WAY);
    }

    public void testHttpsConnection(SSL_TYPE sslType) throws Exception {
        final String keyStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/client.keystore").getPath();
        final String trustStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/client.truststore").getPath();
        final String password = "123456";

        if (SSL_TYPE.TWO_WAY.equals(sslType)) {
            System.setProperty("javax.net.ssl.keyStore", keyStorePath);
            System.setProperty("javax.net.ssl.keyStorePassword", password);
        }

        System.setProperty("javax.net.ssl.trustStore", trustStorePath);
        System.setProperty("javax.net.ssl.trustStorePassword", password);

        prepareServerWithElytronHttpsConnection(container(1), sslType);

        container(1).start();

        ProducerAutoAck producer = new ProducerAutoAck(container(1), QUEUE_JNDI_ADDRESS, 1);
        producer.setUserName("admin");
        producer.setPassword("adminadmin");
        producer.start();

        ReceiverAutoAck receiver = new ReceiverAutoAck(container(1), QUEUE_JNDI_ADDRESS);
        receiver.setUserName("admin");
        receiver.setPassword("adminadmin");
        receiver.start();

        producer.join();
        receiver.join();

        container(1).stop();

        Assert.assertNull("Producer got unexpected exception.", producer.getException());
        Assert.assertNull("Receiver got unexpected exception.", receiver.getException());
        Assert.assertEquals("Number of sent and received message are not equal.", producer.getCount(), receiver.getCount());
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testWrongCertHttpsConnectionServerAuth() throws Exception {
        testWrongCertHttpsConnection(SSL_TYPE.ONE_WAY);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testWrongCertHttpsConnectionBothAuth() throws Exception {
        testWrongCertHttpsConnection(SSL_TYPE.TWO_WAY);
    }

    public void testWrongCertHttpsConnection(SSL_TYPE sslType) throws Exception {
        final String keyStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/server.keystore").getPath();
        final String trustStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/server.truststore").getPath();
        final String password = "123456";

        if (SSL_TYPE.TWO_WAY.equals(sslType)) {
            System.setProperty("javax.net.ssl.keyStore", keyStorePath);
            System.setProperty("javax.net.ssl.keyStorePassword", password);
        }

        System.setProperty("javax.net.ssl.trustStore", trustStorePath);
        System.setProperty("javax.net.ssl.trustStorePassword", password);

        prepareServerWithElytronHttpsConnection(container(1), sslType);

        container(1).start();

        ProducerAutoAck producer = new ProducerAutoAck(container(1), QUEUE_JNDI_ADDRESS, 1);
        producer.setUserName("admin");
        producer.setPassword("adminadmin");
        producer.start();

        producer.join();

        container(1).stop();

        Assert.assertNotNull("Producer got unexpected exception.", producer.getException());
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @BMRules({
            @BMRule(
                    name = "1s rule to force sslv3 - createSSLEngine()",
                    targetClass = "org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector$1",
                    targetMethod = "initChannel",
                    isAfter = true,
//            binding = "engine:SSLEngine = $0",
                    targetLocation = "INVOKE createSSLEngine()",
                    action = "System.out.println(\"mnovak - byteman rule triggered - uuuuhhaaaa\"); org.jboss.qa.artemis.test.security.SslAuthenticationTestCase.setEnabledProtocols($!)"

            ),
            @BMRule(
                    name = "2nd rule to force sslv3  - createSSLEngine(String, int)",
                    targetClass = "org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector$1",
                    targetMethod = "initChannel",
                    isAfter = true,
//            binding = "engine:SSLEngine = $0",
                    targetLocation = "INVOKE createSSLEngine(String, int)",
                    action = "System.out.println(\"mnovak - byteman rule triggered - uuuuhhaaaa\"); org.jboss.qa.artemis.test.security.SslAuthenticationTestCase.setEnabledProtocols($!)"

            )
    })
    public void testSSLv3CertHttpsConnectionServerAuth() throws Exception {
        tesSSLv3CertHttpsConnection(SSL_TYPE.ONE_WAY);
    }

    public void tesSSLv3CertHttpsConnection(SSL_TYPE sslType) throws Exception {
        final String keyStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/client.keystore").getPath();
        final String trustStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/client.truststore").getPath();
        final String password = "123456";

        if (SSL_TYPE.TWO_WAY.equals(sslType)) {
            System.setProperty("javax.net.ssl.keyStore", keyStorePath);
            System.setProperty("javax.net.ssl.keyStorePassword", password);
        }

        System.setProperty("javax.net.ssl.trustStore", trustStorePath);
        System.setProperty("javax.net.ssl.trustStorePassword", password);

        prepareServerWithElytronHttpsConnection(container(1), sslType);

        container(1).start();

        RuleInstaller.installRule(this.getClass(), container(1).getHostname(), BYTEMAN_CLIENT_PORT);

        ProducerAutoAck producer = new ProducerAutoAck(container(1), QUEUE_JNDI_ADDRESS, 1);
        producer.setUserName("admin");
        producer.setPassword("adminadmin");
        producer.start();

        producer.join();

        container(1).stop();

        Assert.assertNotNull("Producer must get exception.", producer.getException());

    }


    public OnlineManagementClient getOnlineManagementClient(Container container) throws Exception {
        return ManagementClient.online(OnlineOptions
                .standalone()
                .hostAndPort(container.getHostname(), container.getPort())
                .build());
    }

    // Server constants
    private static final String SSL_CONTEXT_NAME = "server-ssl-context";
    private static final String HTTPS_LISTENER = "https";
    private static final String HTTPS_SOCKET_BINDING = "https";
    private static final String HTTPS_ACCEPTOR_NAME = "http-acceptor";
    private static final String HTTPS_CONNECTOR_NAME = "https-connector";
    private static final String HTTPS_PROTOCOLS = "TLSv1.1";
    private final String serverKeyStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/server.keystore").getPath();
    private final String serverTrustStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/server.truststore").getPath();
    private static final String PASSWORD = "123456";

    // todo this needs clean up -> move to ActiveMQAdminOperationsEAP7 first, then use prepare framework
    private void prepareServerWithElytronHttpsConnection(Container container, SSL_TYPE sslType) throws Exception {

        // Configure Elytron and set Elytron security domain on messaging-activemq subsystem
        String elytronSecurityDomain = "ApplicationRealm";
        String constantLoginPermissionMapper = "login-permission-mapper";
        String loginPermissionMapperClass = "org.wildfly.security.auth.permission.LoginPermission";
        String constantRealmMapper = "local";
        String constantRealmMapperReamName = "local";
        String simpleRoleDecoderMapper = "groups-to-roles";
        String simpleRoleDecoderMapperAttributes = "groups";

        String propertiesRealmName = "ApplicationRealm";
        String userFilePath = ServerPathUtils.getConfigurationDirectory(container).getAbsolutePath() + File.separator + UsersSettings.USERS_FILE;
        String rolesFilePath = ServerPathUtils.getConfigurationDirectory(container).getAbsolutePath() + File.separator + UsersSettings.ROLES_FILE;
        String simplePermissionMapper = "login-permission-mapper";
        String roleDecoder = "groups-to-roles";

        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();
        jmsOperations.addExtension("org.wildfly.extension.elytron");
        jmsOperations.addSubsystem("elytron");
        jmsOperations.reload();

        jmsOperations.addElytronConstantPermissionMapper(constantLoginPermissionMapper, loginPermissionMapperClass);
        jmsOperations.addElytronConstantRealmMapper(constantRealmMapper, constantRealmMapperReamName);
        jmsOperations.addSimpleRoleDecoderMapper(simpleRoleDecoderMapper, simpleRoleDecoderMapperAttributes);
        jmsOperations.addElytronPropertiesRealm(propertiesRealmName, userFilePath, rolesFilePath);
        jmsOperations.addElytronSecurityDomain(elytronSecurityDomain, propertiesRealmName, simplePermissionMapper, roleDecoder);

        jmsOperations.setElytronSecurityDomain(elytronSecurityDomain);
        jmsOperations.setSecurityEnabled(true);

        /// Configure SSL Eltyron server context

        try (OnlineManagementClient client = getOnlineManagementClient(container)) {

            CreateServerSSLContext createServerSSLContext = null;

            if (SSL_TYPE.TWO_WAY.equals(sslType)) {
                createServerSSLContext = new CreateServerSSLContext.Builder(SSL_CONTEXT_NAME)
                        .keyStoreType("JKS")
                        .keyStorePath(serverKeyStorePath)
                        .keyStorePassword(PASSWORD)
                        .keyPassword(PASSWORD)
                        .trustStorePath(serverTrustStorePath)
                        .trustStorePassword(PASSWORD)
                        .algorithm("SunX509")
                        .protocols(HTTPS_PROTOCOLS)
                        .needClientAuth(true)
                        .build();
            } else { // else create one way ssl server context
                createServerSSLContext = new CreateServerSSLContext.Builder(SSL_CONTEXT_NAME)
                        .keyStoreType("JKS")
                        .keyStorePath(serverKeyStorePath)
                        .keyStorePassword(PASSWORD)
                        .keyPassword(PASSWORD)
                        .algorithm("SunX509")
                        .protocols(HTTPS_PROTOCOLS)
                        .needClientAuth(false)
                        .build();
            }

            client.apply(createServerSSLContext);

//            new Administration(client).reloadIfRequired();
            jmsOperations.reload();

            // Prepare https listener
            jmsOperations.removeHttpsListener(HTTPS_LISTENER);
            jmsOperations.reload();
            jmsOperations.addHttpsListenerWithElytron(HTTPS_LISTENER, HTTPS_SOCKET_BINDING, SSL_CONTEXT_NAME);

        }

        jmsOperations.createHttpAcceptor(HTTPS_ACCEPTOR_NAME, HTTPS_LISTENER, null);
        Map<String, String> httpConnectorParams = new HashMap<String, String>();
        httpConnectorParams.put("ssl-enabled", "true");
        jmsOperations.createHttpConnector(HTTPS_CONNECTOR_NAME, HTTPS_LISTENER, httpConnectorParams, HTTPS_ACCEPTOR_NAME);
        jmsOperations.removeConnectionFactory(Constants.CONNECTION_FACTORY_EAP7);
        jmsOperations.createConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, Constants.CONNECTION_FACTORY_JNDI_FULL_NAME_EAP7, HTTPS_CONNECTOR_NAME);

        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);

        UsersSettings.forDefaultEapServer()
                .withUser(PrepareConstants.USER_NAME, PrepareConstants.USER_PASS, "users")
                .withUser(PrepareConstants.ADMIN_NAME, PrepareConstants.ADMIN_PASS, "admin")
                .create();

        jmsOperations.close();
        container.stop();
    }

}
