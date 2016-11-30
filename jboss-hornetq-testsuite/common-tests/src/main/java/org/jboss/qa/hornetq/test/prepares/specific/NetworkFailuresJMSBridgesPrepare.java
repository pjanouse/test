package org.jboss.qa.hornetq.test.prepares.specific;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.test.prepares.generic.JMSBridge;
import org.jboss.qa.hornetq.test.prepares.generic.TwoNodes;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.HashMap;
import java.util.Map;

public class NetworkFailuresJMSBridgesPrepare extends TwoNodes {

    protected final static String PROTOCOL = "PROTOCOL";

    @Override
    @PrepareMethod(value = "NetworkFailuresJMSBridges", labels = {"EAP6", "EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void beforePrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.beforePrepare(params, ctx);
        PrepareUtils.requireParam(params, PrepareParams.RECONNECT_ATTEMPTS);
        ctx.invokeMethod("NetworkFailuresJMSBridges-beforePrepare", params);
    }

    @PrepareMethod(value = "NetworkFailuresJMSBridges-beforePrepare", labels = {"EAP6"})
    public void beforePrepareEAP6(Map<String, Object> params) throws Exception {
        params.put(PROTOCOL, "remote://");
    }

    @PrepareMethod(value = "NetworkFailuresJMSBridges-beforePrepare", labels = {"EAP7"})
    public void beforePrepareEAP7(Map<String, Object> params) throws Exception {
        params.put(PROTOCOL, "http-remoting://");
    }

    @Override
    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {

        String qos = PrepareUtils.getString(params, PrepareParams.QOS, Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE.name());
        long failureRetryInterval = PrepareUtils.getLong(params, PrepareParams.JMS_BRIDGE_FAILURE_RETRY_INTERVAL, 1000l);
        long maxBatchSize = PrepareUtils.getLong(params, PrepareParams.JMS_BRIDGE_MAX_BATCH_SIZE, 10l);
        long maxBatchTime = PrepareUtils.getLong(params, PrepareParams.JMS_BRIDGE_MAX_BATCH_TIME, 100l);
        boolean addMessageIDInHeader = PrepareUtils.getBoolean(params, PrepareParams.JMS_BRIDGE_ADD_MESSAGE_ID_IN_HEADER, true);

        int reconnectAttempts = PrepareUtils.getInteger(params, PrepareParams.RECONNECT_ATTEMPTS);
        String protocol = PrepareUtils.getString(params, PROTOCOL);

        String sourceConnectionFactory = "java:/ConnectionFactory";
        String bridgeConnectionFactory = "BridgeConnectionFactory";
        String bridgeConnectionFactoryJndiName = "jms/" + bridgeConnectionFactory;

        Container sourceContainer = getContainer(params, 1);
        Container targetContaienr = getContainer(params, 2);

        JMSOperations sourceOps = sourceContainer.getJmsOperations();
        JMSOperations targetOps = targetContaienr.getJmsOperations();

        // every one can connect to remote server through proxy
        String connectorToProxy = "connector-to-proxy-to-target-server";
        String socketBindingToProxy = "binding-connect-to-proxy-to-target-server";
        targetOps.addRemoteSocketBinding(socketBindingToProxy, "127.0.0.1", NetworkFailuresConstants.PROXY_12_PORT);
        targetOps.createHttpConnector(connectorToProxy, socketBindingToProxy, null);

        targetOps.createConnectionFactory(bridgeConnectionFactory, "java:jboss/exported/jms/" + bridgeConnectionFactory, connectorToProxy);
        targetOps.setHaForConnectionFactory(bridgeConnectionFactory, false);
        targetOps.setBlockOnAckForConnectionFactory(bridgeConnectionFactory, true);
        targetOps.setRetryIntervalForConnectionFactory(bridgeConnectionFactory, 1000L);
        targetOps.setRetryIntervalMultiplierForConnectionFactory(bridgeConnectionFactory, 1.0);
        targetOps.setReconnectAttemptsForConnectionFactory(bridgeConnectionFactory, reconnectAttempts);
        targetOps.setFactoryType(bridgeConnectionFactory, "XA_GENERIC");

        Map<String,String> targetContext = new HashMap<String, String>();

        targetContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
        targetContext.put("java.naming.provider.url", protocol + targetContaienr.getHostname() + ":" + targetContaienr.getJNDIPort());

        sourceOps.createJMSBridge(PrepareConstants.JMS_BRIDGE_NAME, sourceConnectionFactory, PrepareConstants.IN_QUEUE_JNDI, null,
                bridgeConnectionFactoryJndiName, PrepareConstants.OUT_QUEUE_JNDI, targetContext, qos, failureRetryInterval, reconnectAttempts,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        sourceOps.close();
        targetOps.close();
    }
}
