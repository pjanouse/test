package org.jboss.qa.hornetq.test.soak.modules;


import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.soak.ClassDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.FileDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.SoakTestModule;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class BridgeSoakModule extends HornetQTestCase implements SoakTestModule {

    public final static String BRIDGE_IN_QUEUE = "soak.bridge.InQueue";

    public final static String BRIDGE_IN_QUEUE_JNDI = "jms/queue/soak/bridge/InQueue";

    public final static String BRIDGE_REMOTE_QUEUE = "soak.bridge.RemoteQueue";

    public final static String BRIDGE_REMOTE_QUEUE_JNDI = "jms/queue/soak/bridge/RemoteQueue";

    public final static String BRIDGE_OUT_QUEUE = "soak.bridge.OutQueue";

    public final static String BRIDGE_OUT_QUEUE_JNDI = "jms/queue/soak/bridge/OutQueue";

    private Container queueContainer;

    private Container remoteContainer;

    // from InQueue to RemoteQueue, default is CORE
    private final BridgeType outboundBridgeType;

    // from RemoteQueue to OutQueue, default is JMS
    private final BridgeType inboundBridgeType;


    public BridgeSoakModule() {
        this(BridgeType.CORE, BridgeType.JMS);
    }


    public BridgeSoakModule(final BridgeType outboundBridgeType, final BridgeType inboundBridgeType) {
        this.outboundBridgeType = outboundBridgeType;
        this.inboundBridgeType = inboundBridgeType;
    }


    @Override
    public void setUpServers() {
        this.queueContainer = container(1);
        this.remoteContainer = container(2);

        this.prepareQueues(this.queueContainer);
        this.prepareRemoteQueues(this.remoteContainer);

        // bridge from inqueue to remotequeue
        JMSOperations ops = queueContainer.getJmsOperations();
        ops.addRemoteSocketBinding("messaging-bridge", remoteContainer.getHostname(), remoteContainer.getHornetqPort());
        ops.createRemoteConnector("bridge-connector", "messaging-bridge", null);
        switch (this.outboundBridgeType) {
            case JMS:
                Map<String, String> targetContext = new HashMap<String, String>(2);
                targetContext.put("java.naming.factory.initial",
                        "org.jboss.naming.remote.client.InitialContextFactory");
                targetContext.put("java.naming.provider.url",
                        "remote://" + remoteContainer.getHostname() + ":" + remoteContainer.getJNDIPort());

                ops.createJMSBridge("soak-outbound-bridge", "java:/ConnectionFactory",
                        "java:/" + BRIDGE_IN_QUEUE_JNDI, null,
                        "java:/jms/RemoteConnectionFactory",
                        "java:/" + BRIDGE_REMOTE_QUEUE_JNDI, targetContext,
                        "AT_MOST_ONCE", 1000, -1, 10, 100, true);
                break;
            case CORE:
            default:
                ops.createCoreBridge("soak-outbound-bridge", "jms.queue." + BRIDGE_IN_QUEUE,
                        "jms.queue." + BRIDGE_REMOTE_QUEUE, -1, "bridge-connector");
                break;
        }
        ops.close();

        // bridge from remotequeue to outqueue
        JMSOperations remoteOps = remoteContainer.getJmsOperations();
        remoteOps.addRemoteSocketBinding("messaging-bridge", queueContainer.getHostname(), queueContainer.getHornetqPort());
        remoteOps.createRemoteConnector("bridge-connector", "messaging-bridge", null);
        switch (this.inboundBridgeType) {
            case CORE:
                remoteOps.createCoreBridge("soak-inbound-bridge", "jms.queue." + BRIDGE_REMOTE_QUEUE,
                        "jms.queue." + BRIDGE_OUT_QUEUE, -1, "bridge-connector");
                break;
            case JMS:
            default:
                JMSOperations localOps = queueContainer.getJmsOperations();
                localOps.setFactoryType("RemoteConnectionFactory", "XA_GENERIC");
                localOps.close();

                Map<String, String> targetContext = new HashMap<String, String>(2);
                targetContext.put("java.naming.factory.initial",
                        "org.jboss.naming.remote.client.InitialContextFactory");
                targetContext.put("java.naming.provider.url",
                        "remote://" + queueContainer.getHostname() + ":" + queueContainer.getJNDIPort());

                remoteOps.setFactoryType("InVmConnectionFactory", "XA_GENERIC");
                remoteOps.createJMSBridge("soak-inbound-bridge", "java:/ConnectionFactory",
                        "java:/" + BRIDGE_REMOTE_QUEUE_JNDI, null,
                        "jms/RemoteConnectionFactory",
                        "java:/" + BRIDGE_OUT_QUEUE_JNDI, targetContext,
                        "ONCE_AND_ONLY_ONCE", 1000, -1, 10, 100, true);
                break;

        }
        remoteOps.close();
    }


    @Override
    public List<ClassDeploymentDefinition> getRequiredClasses() {
        return new ArrayList<ClassDeploymentDefinition>();
    }


    @Override
    public List<FileDeploymentDefinition> getRequiredAssets() {
        return new ArrayList<FileDeploymentDefinition>();
    }


    private void prepareQueues(final Container container) {
        JMSOperations ops = container.getJmsOperations();
        ops.createQueue(BRIDGE_IN_QUEUE, BRIDGE_IN_QUEUE_JNDI);
        ops.createQueue(BRIDGE_OUT_QUEUE, BRIDGE_OUT_QUEUE_JNDI);
        ops.close();
    }


    private void prepareRemoteQueues(final Container container) {
        JMSOperations ops = container.getJmsOperations();
        ops.createQueue(BRIDGE_REMOTE_QUEUE, BRIDGE_REMOTE_QUEUE_JNDI);
        ops.close();
    }


    public static enum BridgeType {

        CORE,
        JMS;

    }

}
