package org.jboss.qa.hornetq.test.prepares.specific;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.constants.Constants.SSL_TYPE;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.test.prepares.generic.OneNode;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ServerPathUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by mnovak on 12/2/16.
 */
public class ElytronSubsystemWithSSLContextPrepare extends OneNode {

    // Server constants
    private static final String SSL_CONTEXT_NAME = "server-ssl-context";
    private static final String HTTPS_LISTENER = "https";
    private static final String HTTPS_SOCKET_BINDING = "https";
    private static final String HTTPS_ACCEPTOR_NAME = "http-acceptor";
    private static final String HTTPS_CONNECTOR_NAME = "https-connector";
    private static final String HTTPS_PROTOCOLS = "TLSv1.1";
    private static final String ALGORITHM = "SunX509";
    private static final String KEY_STORE_TYPE = "JKS";


    private final String serverKeyStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/server.keystore").getPath();
    private final String serverTrustStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/server.truststore").getPath();
    private static final String PASSWORD = "123456";

    @Override
    @PrepareMethod(value = "ElytronSubsystemWithSSLContextPrepare", labels = {"EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) {
        PrepareUtils.requireParam(params, PrepareParams.ELYTRON_SECURITY_DOMAIN);
        PrepareUtils.requireParam(params, PrepareParams.SSL_TYPE);
        PrepareUtils.setIfNotSpecified(params, PrepareParams.ELYTRON_SECURITY_DOMAIN, "ApplicationRealm");
        PrepareUtils.setIfNotSpecified(params, PrepareParams.SSL_TYPE, PrepareConstants.SSL_TYPE);
        String elytronSecurityDomain = (String) params.get(PrepareParams.ELYTRON_SECURITY_DOMAIN);
        String sslType = (String) params.get(PrepareParams.SSL_TYPE);

        Container container = (Container) params.get("container");

        // Configure Elytron and set Elytron security domain on messaging-activemq subsystem
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

        JMSOperations jmsOperations = getJMSOperations(params);
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

        /// Configure SSL Elytron server context

        if (SSL_TYPE.TWO_WAY.equals(sslType)) {
            jmsOperations.createServerSSLContext(SSL_CONTEXT_NAME, KEY_STORE_TYPE, serverKeyStorePath, PASSWORD, serverTrustStorePath, PASSWORD, ALGORITHM,
                    HTTPS_PROTOCOLS, true);
        } else { // else create one way ssl server context
            jmsOperations.createServerSSLContext(SSL_CONTEXT_NAME, KEY_STORE_TYPE, serverKeyStorePath, PASSWORD, ALGORITHM,
                    HTTPS_PROTOCOLS, false);
        }

        jmsOperations.reload();

        // Prepare https listener
        jmsOperations.removeHttpsListener(HTTPS_LISTENER);
        jmsOperations.reload();
        jmsOperations.addHttpsListenerWithElytron(HTTPS_LISTENER, HTTPS_SOCKET_BINDING, SSL_CONTEXT_NAME);


        jmsOperations.createHttpAcceptor(HTTPS_ACCEPTOR_NAME, HTTPS_LISTENER, null);
        Map<String, String> httpConnectorParams = new HashMap<String, String>();
        httpConnectorParams.put("ssl-enabled", "true");
        jmsOperations.createHttpConnector(HTTPS_CONNECTOR_NAME, HTTPS_LISTENER, httpConnectorParams, HTTPS_ACCEPTOR_NAME);
        jmsOperations.removeConnectionFactory(Constants.CONNECTION_FACTORY_EAP7);
        jmsOperations.createConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, Constants.CONNECTION_FACTORY_JNDI_FULL_NAME_EAP7, HTTPS_CONNECTOR_NAME);

        jmsOperations.close();

    }
}

