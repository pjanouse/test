package org.jboss.qa.hornetq.test.prepares.specific;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.test.prepares.generic.FourNodes;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class Lodh4Prepare extends FourNodes {

    public static final int NUMBER_OF_DESTINATIONS = 5;

    public static final String IN_QUEUE_NAME_PREFIX = "InQueue";
    public static final String IN_QUEUE_JNDI_PREFIX = "queue/InQueue";
    public static final String OUT_QUEUE_NAME_PREFIX = "OutQueue";
    public static final String OUT_QUEUE_JNDI_PREFIX = "queue/OutQueue";

    public static final String VARIANT = "VARIANT";

    public enum Variant {
        DEFAULT,
        THREE_NODES
    }

    @Override
    @PrepareMethod(value = "Lodh4Prepare", labels = {"EAP6", "EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void beforePrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.beforePrepare(params, ctx);

        PrepareUtils.setIfNotSpecified(params, PrepareParams.CLUSTER_TYPE, Constants.CLUSTER_TYPE.MULTICAST.name());
        PrepareUtils.setIfNotSpecified(params, PrepareParams.PREPARE_DESTINATIONS, false);
    }

    @Override
    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.afterPrepare(params, ctx);

        ctx.invokeMethod("Lodh4Prepare-afterPrepare", params);
    }

    @PrepareMethod(value = "Lodh4Prepare-afterPrepare", labels = {"EAP6"})
    public void afterPrepareEAP6(Map<String, Object> params) throws Exception {
        Variant variant = PrepareUtils.getEnum(params, VARIANT, Variant.class, Variant.DEFAULT);

        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);
        Container container3 = getContainer(params, 3);
        Container container4 = getContainer(params, 4);

        switch (variant) {
            case DEFAULT:
                prepareTargetServer(container2);
                prepareTargetServer(container4);
                prepareSourceServerEAP6(container1, container2);
                prepareSourceServerEAP6(container3, container4);
                break;
            case THREE_NODES:
                prepareTargetServer(container4);
                prepareSourceServerEAP6(container1, null);
                prepareSourceServerEAP6(container3, container4);
                break;
            default: throw new IllegalArgumentException("Unsupported variant.");
        }
    }

    @PrepareMethod(value = "Lodh4Prepare-afterPrepare", labels = {"EAP7"})
    public void afterPrepareEAP7(Map<String, Object> params) throws Exception {
        Variant variant = PrepareUtils.getEnum(params, VARIANT, Variant.class, Variant.DEFAULT);

        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);
        Container container3 = getContainer(params, 3);
        Container container4 = getContainer(params, 4);

        switch (variant) {
            case DEFAULT:
                prepareTargetServer(container2);
                prepareTargetServer(container4);
                prepareSourceServerEAP7(container1, container2);
                prepareSourceServerEAP7(container3, container4);
                break;
            case THREE_NODES:
                prepareTargetServer(container4);
                prepareSourceServerEAP7(container1, null);
                prepareSourceServerEAP7(container3, container4);
                break;
            default: throw new IllegalArgumentException("Unsupported variant.");
        }

    }

    private void prepareSourceServerEAP6(Container container, Container target) throws Exception {
        String remoteSocketBinding = "messaging-remote";
        String remoteConnector = "connector-remote";

        if (target == null) {
            return;
        }

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setMulticastAddressOnSocketBinding(PrepareConstants.MULTICAST_SOCKET_BINDING_NAME, "231.43.21.36");
        jmsOperations.setIdCacheSize(500000);

        jmsOperations.addRemoteSocketBinding(remoteSocketBinding, target.getHostname(), target.getHornetqPort());
        jmsOperations.createRemoteConnector(remoteConnector, remoteSocketBinding, null);

        for (int queueNumber = 0; queueNumber < Lodh4Prepare.NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsOperations.createQueue("default", Lodh4Prepare.IN_QUEUE_NAME_PREFIX + queueNumber, Lodh4Prepare.IN_QUEUE_JNDI_PREFIX + queueNumber, true);
        }

        jmsOperations.close();
        container.restart();
        Thread.sleep(3000);
        jmsOperations = container.getJmsOperations();

        for (int i = 0; i < Lodh4Prepare.NUMBER_OF_DESTINATIONS; i++) {
            jmsOperations.createCoreBridge("myBridge" + i, "jms.queue." + Lodh4Prepare.IN_QUEUE_NAME_PREFIX + i, "jms.queue." + Lodh4Prepare.OUT_QUEUE_NAME_PREFIX + i, -1, remoteConnector);
        }

        jmsOperations.close();
    }

    private void prepareSourceServerEAP7(Container container, Container target) throws Exception {
        String remoteSocketBinding = "messaging-remote";
        String remoteConnector = "connector-remote";

        if (target == null) {
            return;
        }

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setMulticastAddressOnSocketBinding(PrepareConstants.MULTICAST_SOCKET_BINDING_NAME, "231.43.21.36");
        jmsOperations.setIdCacheSize(500000);

        jmsOperations.addRemoteSocketBinding(remoteSocketBinding, target.getHostname(), target.getHornetqPort());
        jmsOperations.createHttpConnector(remoteConnector, remoteSocketBinding, null, "acceptor");

        for (int queueNumber = 0; queueNumber < Lodh4Prepare.NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsOperations.createQueue("default", Lodh4Prepare.IN_QUEUE_NAME_PREFIX + queueNumber, Lodh4Prepare.IN_QUEUE_JNDI_PREFIX + queueNumber, true);
        }

        jmsOperations.close();
        container.restart();
        Thread.sleep(3000);
        jmsOperations = container.getJmsOperations();

        for (int i = 0; i < Lodh4Prepare.NUMBER_OF_DESTINATIONS; i++) {
            jmsOperations.createCoreBridge("myBridge" + i, "jms.queue." + Lodh4Prepare.IN_QUEUE_NAME_PREFIX + i, "jms.queue." + Lodh4Prepare.OUT_QUEUE_NAME_PREFIX + i, -1, remoteConnector);
        }

        jmsOperations.close();
    }

    private void prepareTargetServer(Container container) {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setIdCacheSize(500000);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsOperations.createQueue("default", OUT_QUEUE_NAME_PREFIX + queueNumber, OUT_QUEUE_JNDI_PREFIX + queueNumber, true);
        }

        jmsOperations.close();
    }
}
