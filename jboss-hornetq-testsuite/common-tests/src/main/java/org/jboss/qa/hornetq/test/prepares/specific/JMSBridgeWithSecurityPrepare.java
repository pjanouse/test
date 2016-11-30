package org.jboss.qa.hornetq.test.prepares.specific;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.test.prepares.generic.JMSBridge;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.HashMap;
import java.util.Map;

public class JMSBridgeWithSecurityPrepare extends JMSBridge {

    public enum TestType {SOURCE_SECURITY, TARGET_SECURITY, SOURCE_TARGET_SECURITY, CORRECT_SECURITY}

    public static final String TEST_TYPE = "TEST_TYPE";

    @Override
    @PrepareMethod(value = "JMSBridgeWithSecurity", labels = {"EAP6", "EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void afterPrepareContainer(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.afterPrepareContainer(params, ctx);

        Container container = getContainer(params);
        JMSOperations jmsOperations = getJMSOperations(params);

        jmsOperations.setSecurityEnabled(true);

        // set security persmissions for roles admin,users - user is already there
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsOperations.addRoleToSecuritySettings("#", "bridge");
        jmsOperations.addRoleToSecuritySettings("#", "admin");
        jmsOperations.addRoleToSecuritySettings("#", "users");

        jmsOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "consume", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "create-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "create-non-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "delete-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "delete-non-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "manage", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "send", false);

        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "consume", true);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-durable-queue", true);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-non-durable-queue", true);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-durable-queue", true);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-non-durable-queue", true);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "manage", true);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "send", true);

        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "consume", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-non-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-non-durable-queue", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "manage", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "send", false);


        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("password-stacking", "useFirstPass");
        opts.put("unauthenticatedIdentity", "guest");
        jmsOperations.rewriteLoginModule("Remoting", opts);
        jmsOperations.rewriteLoginModule("RealmDirect", opts);

        UsersSettings.forEapServer(container)
                .withUser("guest", null, "guest")
                .withUser("user", "user", "users")
                .withUser("admin", "adminadmin", "admin")
                .withUser("bridge", "bridge", "bridge")
                .create();
    }

    @Override
    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        Constants.QUALITY_OF_SERVICE qos = Constants.QUALITY_OF_SERVICE.valueOf(PrepareUtils.getString(params, PrepareParams.QOS, Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE.name()));
        long failureRetryInterval = PrepareUtils.getLong(params, PrepareParams.JMS_BRIDGE_FAILURE_RETRY_INTERVAL, 1000l);
        long maxBatchSize = PrepareUtils.getLong(params, PrepareParams.JMS_BRIDGE_MAX_BATCH_SIZE, 10l);
        long maxBatchTime = PrepareUtils.getLong(params, PrepareParams.JMS_BRIDGE_MAX_BATCH_TIME, 100l);
        boolean addMessageIDInHeader = PrepareUtils.getBoolean(params, PrepareParams.JMS_BRIDGE_ADD_MESSAGE_ID_IN_HEADER, true);
        int maxRetries = PrepareUtils.getInteger(params, PrepareParams.JMS_BRIDGE_MAX_RETRIES, -1);
        Map<String, String> targetContext = PrepareUtils.get(params, TARGET_CONTEXT, Map.class);
        TestType testType = TestType.valueOf(PrepareUtils.getString(params, TEST_TYPE));

        Container sourceContainer = getContainer(params, 1);

        JMSOperations jmsOperations = sourceContainer.getJmsOperations();

        switch (testType) {
            case TARGET_SECURITY:
                jmsOperations.createJMSBridge(PrepareConstants.JMS_BRIDGE_NAME, SOURCE_CONNECTION_FACTORY, PrepareConstants.IN_QUEUE_JNDI, null,
                        TARGET_CONNECTION_FACTORY, PrepareConstants.OUT_QUEUE_JNDI, targetContext, qos.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, null, null, "bridge", "bridge");
                break;
            case SOURCE_SECURITY:
                jmsOperations.createJMSBridge(PrepareConstants.JMS_BRIDGE_NAME, SOURCE_CONNECTION_FACTORY, PrepareConstants.IN_QUEUE_JNDI, null,
                        TARGET_CONNECTION_FACTORY, PrepareConstants.OUT_QUEUE_JNDI, targetContext, qos.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, "bridge", "bridge", null, null);
                break;
            case SOURCE_TARGET_SECURITY:
                jmsOperations.createJMSBridge(PrepareConstants.JMS_BRIDGE_NAME, SOURCE_CONNECTION_FACTORY, PrepareConstants.IN_QUEUE_JNDI, null,
                        TARGET_CONNECTION_FACTORY, PrepareConstants.OUT_QUEUE_JNDI, targetContext, qos.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, "bridge", "bridge", "bridge", "bridge");
                break;
            case CORRECT_SECURITY:
                jmsOperations.createJMSBridge(PrepareConstants.JMS_BRIDGE_NAME, SOURCE_CONNECTION_FACTORY, PrepareConstants.IN_QUEUE_JNDI, null,
                        TARGET_CONNECTION_FACTORY, PrepareConstants.OUT_QUEUE_JNDI, targetContext, qos.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, "admin", "adminadmin", "admin", "adminadmin");
        }

        jmsOperations.close();

    }

}
