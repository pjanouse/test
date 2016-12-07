package org.jboss.qa.hornetq.test.prepares.specific;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.test.prepares.generic.OneNode;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ServerPathUtils;

import java.io.File;
import java.util.Map;

/**
 * Created by mnovak on 12/2/16.
 */
public class ElytronSubsystemPrepare extends OneNode {

    @Override
    @PrepareMethod(value = "ElytronSubsystemPrepare", labels = {"EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) {
        PrepareUtils.requireParam(params, PrepareParams.ELYTRON_SECURITY_DOMAIN);
        PrepareUtils.setIfNotSpecified(params, PrepareParams.ELYTRON_SECURITY_DOMAIN, "ApplicationRealm");
        String elytronSecurityDomain = (String) params.get(PrepareParams.ELYTRON_SECURITY_DOMAIN);

        Container container = (Container) params.get("container");

        JMSOperations jmsOperations = getJMSOperations(params);

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

        jmsOperations.addExtension("org.wildfly.extension.elytron");
        jmsOperations.addSubsystem("elytron");
        jmsOperations.reload();

        jmsOperations.addElytronConstantPermissionMapper(constantLoginPermissionMapper, loginPermissionMapperClass);
        jmsOperations.addElytronConstantRealmMapper(constantRealmMapper, constantRealmMapperReamName);
        jmsOperations.addSimpleRoleDecoderMapper(simpleRoleDecoderMapper, simpleRoleDecoderMapperAttributes);
        jmsOperations.addElytronPropertiesRealm(propertiesRealmName, userFilePath, rolesFilePath);
        jmsOperations.addElytronSecurityDomain(elytronSecurityDomain, propertiesRealmName, simplePermissionMapper, roleDecoder);

        jmsOperations.setElytronSecurityDomain(elytronSecurityDomain);
        jmsOperations.close();

    }
}
