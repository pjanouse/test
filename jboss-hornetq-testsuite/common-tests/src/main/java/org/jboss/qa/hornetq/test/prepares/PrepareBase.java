package org.jboss.qa.hornetq.test.prepares;

import org.jboss.logging.Logger;
import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.HashMap;
import java.util.Map;

public class PrepareBase {

    public static final Logger log = Logger.getLogger(PrepareBase.class);

    public Container getContainer(Map<String, Object> params, int i) {
        return PrepareUtils.get(params, "container" + i, Container.class);
    }

    public Container getContainer(Map<String, Object> params) {
        return PrepareUtils.get(params, "container", Container.class);
    }

    public JMSOperations getJMSOperations(Map<String, Object> params) {
        return PrepareUtils.get(params, "jmsOperations", JMSOperations.class);
    }

    public void setJMSOperations(Map<String, Object> params, JMSOperations jmsOperations) {
        params.put("jmsOperations", jmsOperations);
    }

    public String getServerName(Map<String, Object> params) {
        return PrepareUtils.getString(params, "serverName", "default");
    }

    public Map<String, Object> getParamsForContainer(Map<String, Object> params, Container container, JMSOperations jmsOperations, int i) {
        Map<String, Object> newParams = new HashMap<String, Object>(params);
        String prefix = "" + i + ".";

        for (String key : params.keySet()) {
            if (key.startsWith(prefix)) {
                newParams.put(key.substring(prefix.length()), newParams.get(key));
            }
        }

        newParams.put("container", container);
        newParams.put("jmsOperations", jmsOperations);
        return newParams;
    }

    public Map<String, Object> getParamsForServer(Map<String, Object> params, String serverName) {
        Map<String, Object> newParams = new HashMap<String, Object>(params);
        newParams.put("serverName", serverName);
        return newParams;
    }

    public void invokeAllPrepareMethods(Map<String, Object> params, PrepareContext ctx) throws Exception {

        ctx.invokeMethod(PrepareMethods.PREPARE_DESTINATIONS, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_DIVERTS, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_ADDRESS_SETTINGS, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_SECURITY, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_CONNECTION_FACTORY, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_CONNECTOR, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_CLUSTER, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_MISC, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_PERSISTENCE, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_DATABASE, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_HA, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_JOURNALS_DIRECTORY, params);
        ctx.invokeMethod(PrepareMethods.PREPARE_COLOCATED_BACKUP, params);
    }

}
