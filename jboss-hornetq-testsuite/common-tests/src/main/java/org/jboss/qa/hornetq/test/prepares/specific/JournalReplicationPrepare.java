package org.jboss.qa.hornetq.test.prepares.specific;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.generic.ReplicatedHA;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Arrays;
import java.util.Map;

public class JournalReplicationPrepare extends ReplicatedHA {

    public static final int MESSAGING_TO_LIVE_PROXY_PORT = 51111;

    public static final int MESSAGING_TO_BACKUP_PROXY_PORT = 51112;

    @Override
    @PrepareMethod(value = "JournalReplicationPrepare", labels = {"EAP6", "EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.afterPrepare(params, ctx);

        ctx.invokeMethod("JournalReplicationPrepare-afterPrepare", params);
    }

    @PrepareMethod(value = "JournalReplicationPrepare-afterPrepare", labels = {"EAP6"})
    public void afterPrepareEAP6(Map<String, Object> params) throws Exception {

        Container live = getContainer(params, 1);
        Container backup = getContainer(params, 2);

        prepareProxyConnectorEAP6(live, MESSAGING_TO_LIVE_PROXY_PORT);
        prepareProxyConnectorEAP6(backup, MESSAGING_TO_BACKUP_PROXY_PORT);

    }

    @PrepareMethod(value = "JournalReplicationPrepare-afterPrepare", labels = {"EAP7"})
    public void afterPrepareEAP7(Map<String, Object> params) throws Exception {

        Container live = getContainer(params, 1);
        Container backup = getContainer(params, 2);

        prepareProxyConnectorEAP7(live, MESSAGING_TO_LIVE_PROXY_PORT);
        prepareProxyConnectorEAP7(backup, MESSAGING_TO_BACKUP_PROXY_PORT);
    }

    private void prepareProxyConnectorEAP6(Container container, int proxyPort) {
        String proxyBinding = "messaging-via-proxy";
        String proxyConnector = "proxy-connector";

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addRemoteSocketBinding(proxyBinding, container.getHostname(), proxyPort);
        jmsOperations.createRemoteConnector(proxyConnector, proxyBinding, null);
        jmsOperations.setConnectorOnBroadcastGroup(PrepareConstants.BROADCAST_GROUP_NAME, Arrays.asList(proxyConnector));

        jmsOperations.close();
    }

    private void prepareProxyConnectorEAP7(Container container, int proxyPort) {
        String proxyBinding = "messaging-via-proxy";
        String proxyConnector = "proxy-connector";

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addRemoteSocketBinding(proxyBinding, container.getHostname(), proxyPort);
        jmsOperations.createHttpConnector(proxyConnector, proxyBinding, null, PrepareConstants.ACCEPTOR_NAME);
        jmsOperations.setConnectorOnBroadcastGroup(PrepareConstants.BROADCAST_GROUP_NAME, Arrays.asList(proxyConnector));

        jmsOperations.close();
    }
}
