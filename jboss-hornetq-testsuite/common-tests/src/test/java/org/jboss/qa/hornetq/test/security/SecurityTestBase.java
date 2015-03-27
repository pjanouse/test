package org.jboss.qa.hornetq.test.security;


import org.apache.commons.io.FileUtils;
import org.hornetq.core.remoting.impl.netty.TransportConstants;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * Base class for security test cases.
 *
 * Contains methods for preparing connectors and acceptors over SSL and copying keystores
 * to proper location.
 *
 * See {@link SslAuthenticationTestCase} for examples how to use it in tests.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public abstract class SecurityTestBase extends HornetQTestCase {

    private static final String SOCKET_BINDING_NAME = "messaging";

    public static final String TEST_KEYSTORES_DIRECTORY =
            "src/test/resources/org/jboss/qa/hornetq/test/transportprotocols";

    protected static final String KEY_STORE_PASSWORD = "hornetqexample";

    protected static final String TRUST_STORE_NAME = "hornetq.example.truststore";

    protected static final String KEY_STORE_NAME = "hornetq.example.keystore";

    protected static final String TRUST_STORE_PASSWORD = KEY_STORE_PASSWORD;

    private final String serverKeystoreDir = container(1).getServerHome() + File.separator + "standalone" + File.separator
            + "deployments" + File.separator;

    protected final String trustStorePath = serverKeystoreDir + TRUST_STORE_NAME;

    protected final String keyStorePath = serverKeystoreDir + KEY_STORE_NAME;

    /**
     * Copies keystore and truststore to JBOSS_HOME_1/standalone/deployments.
     *
     * Use {@link #keyStorePath} and {@link #trustStorePath} to get proper paths.
     *
     * @throws IOException
     */
    protected void prepareServerSideKeystores() throws IOException {
        File sourceKeystore = new File(TEST_KEYSTORES_DIRECTORY + File.separator + KEY_STORE_NAME);
        File targetKeystore = new File(keyStorePath);
        FileUtils.copyFile(sourceKeystore, targetKeystore);

        File sourceTruststore = new File(TEST_KEYSTORES_DIRECTORY + File.separator + TRUST_STORE_NAME);
        File targetTruststore = new File(trustStorePath);
        FileUtils.copyFile(sourceTruststore, targetTruststore);
    }


    protected void createOneWaySslConnector(final JMSOperations ops) {
        this.createOneWaySslConnector("netty", ops);
    }


    protected void createOneWaySslConnector(final String connectorName, final JMSOperations ops) {
        Map<String, String> props = new HashMap<String, String>();
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStorePath);
        props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, KEY_STORE_PASSWORD);
        this.createConnector(connectorName, ops, props);
    }


    protected void createOneWaySslAcceptor(final JMSOperations ops) {
        this.createOneWaySslAcceptor("netty", ops);
    }


    protected void createOneWaySslAcceptor(final String acceptorName, final JMSOperations ops) {

        Map<String, String> props = new HashMap<String, String>();
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStorePath);
        props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, KEY_STORE_PASSWORD);
        this.createAcceptor(acceptorName, ops, props);
    }


    protected void createTwoWaySslAcceptor(final JMSOperations ops) {
        this.createTwoWaySslAcceptor("netty", ops);
    }


    protected void createTwoWaySslAcceptor(final String acceptorName, final JMSOperations ops) {
        Map<String, String> props = new HashMap<String, String>();
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, trustStorePath);
        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
        props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStorePath);
        props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, KEY_STORE_PASSWORD);
        props.put("need-client-auth", "true");
        this.createAcceptor(acceptorName, ops, props);
    }


    private void createConnector(final String connectorName, final JMSOperations ops,
            final Map<String, String> properties) {

        try {
            ops.removeRemoteConnector(connectorName);
        } catch (Exception e) {
        }

        ops.createRemoteConnector(connectorName, SOCKET_BINDING_NAME, properties);
    }


    private void createAcceptor(final String acceptorName, final JMSOperations ops,
            final Map<String, String> properties) {

        try {
            ops.removeRemoteAcceptor(acceptorName);
        } catch (Exception e) {
        }

        ops.createRemoteAcceptor(acceptorName, SOCKET_BINDING_NAME, properties);
    }

}
