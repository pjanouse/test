package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;

import javax.naming.Context;
import java.util.HashMap;
import java.util.Map;

public class JMSBridge extends TwoNodes {

    public static final String SOURCE_CONNECTION_FACTORY = "java:/ConnectionFactory";
    public static final String TARGET_CONNECTION_FACTORY = "jms/RemoteConnectionFactory";

    protected static final String TARGET_CONTEXT = "TARGET_CONTEXT";

    @Override
    @PrepareMethod(value = "JMSBridge", labels = {"EAP6"})
    public void prepareMethodEAP6(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP6(params);
    }

    @Override
    @PrepareMethod(value = "JMSBridge", labels = {"EAP7"})
    public void prepareMethodEAP7(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP7(params);
    }

    @Override
    protected void beforePrepareEAP6(Map<String, Object> params) throws Exception {
        super.beforePrepareEAP6(params);

        Container targetContainer = getContainer(params, 2);

        Map<String, String> targetContext = new HashMap<String, String>();
        targetContext.put(Context.INITIAL_CONTEXT_FACTORY, Constants.INITIAL_CONTEXT_FACTORY_EAP6);
        if (JMSTools.isIpv6Address(targetContainer.getHostname())){
            targetContext.put(Context.PROVIDER_URL, String.format("%s[%s]:%s", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6, targetContainer.getHostname(), targetContainer.getJNDIPort()));
        } else {
            targetContext.put(Context.PROVIDER_URL, String.format("%s%s:%s", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6, targetContainer.getHostname(), targetContainer.getJNDIPort()));
        }

        params.put(TARGET_CONTEXT, targetContext);
    }

    @Override
    protected void beforePrepareEAP7(Map<String, Object> params) throws Exception {
        super.beforePrepareEAP7(params);

        Container targetContainer = getContainer(params, 2);

        Map<String, String> targetContext = new HashMap<String, String>();
        targetContext.put(Context.INITIAL_CONTEXT_FACTORY, Constants.INITIAL_CONTEXT_FACTORY_EAP7);
        if (JMSTools.isIpv6Address(targetContainer.getHostname())){
            targetContext.put(Context.PROVIDER_URL, String.format("%s[%s]:%s", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7, targetContainer.getHostname(), targetContainer.getJNDIPort()));
        } else {
            targetContext.put(Context.PROVIDER_URL, String.format("%s%s:%s", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7, targetContainer.getHostname(), targetContainer.getJNDIPort()));
        }

        params.put(TARGET_CONTEXT, targetContext);
    }

    @Override
    protected void afterPrepareContainer(Map<String, Object> params, Container container) throws Exception {
        super.afterPrepareContainer(params, container);

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setClustered(false);
        jmsOperations.removeClusteringGroup(CLUSTER_NAME);
        jmsOperations.removeBroadcastGroup(BROADCAST_GROUP_NAME);
        jmsOperations.removeDiscoveryGroup(DISCOVERY_GROUP_NAME);

        jmsOperations.setFactoryType(INVM_CONNECTION_FACTORY_NAME, "XA_GENERIC");
        jmsOperations.setFactoryType(REMOTE_CONNECTION_FACTORY_NAME, "XA_GENERIC");

        jmsOperations.close();
    }

    @Override
    protected void afterPrepare(Map<String, Object> params) throws Exception {
        super.afterPrepare(params);

        String qos = PrepareUtils.getString(params, PrepareParams.QOS, Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE.name());
        long failureRetryInterval = PrepareUtils.getLong(params, PrepareParams.JMS_BRIDGE_FAILURE_RETRY_INTERVAL, 1000l);
        long maxBatchSize = PrepareUtils.getLong(params, PrepareParams.JMS_BRIDGE_MAX_BATCH_SIZE, 10l);
        long maxBatchTime = PrepareUtils.getLong(params, PrepareParams.JMS_BRIDGE_MAX_BATCH_TIME, 100l);
        boolean addMessageIDInHeader = PrepareUtils.getBoolean(params, PrepareParams.JMS_BRIDGE_ADD_MESSAGE_ID_IN_HEADER, true);
        int maxRetries = PrepareUtils.getInteger(params, PrepareParams.JMS_BRIDGE_MAX_RETRIES, -1);
        Map<String, String> targetContext = PrepareUtils.get(params, TARGET_CONTEXT, Map.class);

        Container sourceContainer = getContainer(params, 1);

        JMSOperations jmsOperations = sourceContainer.getJmsOperations();

        jmsOperations.createJMSBridge(JMS_BRIDGE_NAME, SOURCE_CONNECTION_FACTORY, IN_QUEUE_JNDI, null, TARGET_CONNECTION_FACTORY, OUT_QUEUE_JNDI, targetContext, qos, failureRetryInterval, maxRetries, maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsOperations.close();

    }

}
