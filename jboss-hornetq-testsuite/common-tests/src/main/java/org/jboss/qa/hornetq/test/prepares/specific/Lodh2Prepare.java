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

import static org.jboss.qa.hornetq.constants.Constants.RESOURCE_ADAPTER_NAME_EAP7;

public class Lodh2Prepare extends FourNodes {

    public static final String IN_SERVER = "IN_SERVER";

    public static final String OUT_SERVER = "OUT_SERVER";

    @Override
    @PrepareMethod(value = "Lodh2Prepare", labels = {"EAP6", "EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void beforePrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.beforePrepare(params, ctx);

        PrepareUtils.setIfNotSpecified(params, PrepareParams.CLUSTER_TYPE, Constants.CLUSTER_TYPE.MULTICAST.name());
    }

    @Override
    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.afterPrepare(params, ctx);

        ctx.invokeMethod("Lodh2Prepare-afterPrepare", params);
    }

    @PrepareMethod(value = "Lodh2Prepare-afterPrepare", labels = {"EAP6"})
    public void afterPrepareEAP6(Map<String, Object> params) throws Exception {
        int inServer = PrepareUtils.getInteger(params, IN_SERVER);
        int outServer = PrepareUtils.getInteger(params, OUT_SERVER);

        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);
        Container container3 = getContainer(params, 3);
        Container container4 = getContainer(params, 4);

        prepareJmsServer(container1);
        prepareJmsServer(container3);

        prepareMdbServerEAP6(container2, container1, inServer, outServer);
        prepareMdbServerEAP6(container4, container3, inServer, outServer);
    }

    @PrepareMethod(value = "Lodh2Prepare-afterPrepare", labels = {"EAP7"})
    public void afterPrepareEAP7(Map<String, Object> params) throws Exception {
        int inServer = PrepareUtils.getInteger(params, IN_SERVER);
        int outServer = PrepareUtils.getInteger(params, OUT_SERVER);

        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);
        Container container3 = getContainer(params, 3);
        Container container4 = getContainer(params, 4);

        prepareJmsServer(container1);
        prepareJmsServer(container3);

        prepareMdbServerEAP7(container2, container1, inServer, outServer);
        prepareMdbServerEAP7(container4, container3, inServer, outServer);
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServer(Container container) {

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setMulticastAddressOnSocketBinding(PrepareConstants.MULTICAST_SOCKET_BINDING_NAME, "233.6.88.3");

        jmsAdminOperations.close();
    }


    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP6(Container container, Container jmsServer, int inServer, int outServer) {

        String remoteConnectorName = "connector-remote";
        String remoteSocketBindingName = "messaging-remote";
        String inVmRaName = "local-hornetq-ra";

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setMulticastAddressOnSocketBinding(PrepareConstants.MULTICAST_SOCKET_BINDING_NAME, "233.6.88.5");
        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);

        // both are remote
        if (isServerRemote(inServer) && isServerRemote(outServer)) {
            jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingName, jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, remoteSocketBindingName, null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, -1);
        }
        // local InServer and remote OutServer
        if (!isServerRemote(inServer) && isServerRemote(outServer)) {
            jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingName, jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, remoteSocketBindingName, null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, -1);

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmRaName, "java:/LocalJmsXA", PrepareConstants.INVM_CONNECTOR_NAME);
            jmsAdminOperations.setDefaultResourceAdapter(inVmRaName);
        }

        // remote InServer and local OutServer
        if (isServerRemote(inServer) && !isServerRemote(outServer)) {

            // now reconfigure hornetq-ra which is used for inbound to connect to remote server
            jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingName, jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, remoteSocketBindingName, null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, -1);
            jmsAdminOperations.setJndiNameForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, "java:/remoteJmsXA");

            jmsAdminOperations.close();
            container.restart();
            jmsAdminOperations = container.getJmsOperations();

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmRaName, "java:/JmsXA", PrepareConstants.INVM_CONNECTOR_NAME);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(inVmRaName, -1);
        }

        jmsAdminOperations.close();
        container.stop();

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP7(Container container, Container jmsServer, int inServer, int outServer) {

        String remoteConnectorName = "connector-remote";
        String remoteSocketBindingName = "messaging-remote";
        String inVmRaName = "local-activemq-ra";

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setMulticastAddressOnSocketBinding(PrepareConstants.MULTICAST_SOCKET_BINDING_NAME, "233.6.88.5");
        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);

        // both are remote
        if (isServerRemote(inServer) && isServerRemote(outServer)) {
            jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingName, jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, remoteSocketBindingName, null, "acceptor");
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, -1);
        }
        // local InServer and remote OutServer
        if (!isServerRemote(inServer) && isServerRemote(outServer)) {
            jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingName, jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, remoteSocketBindingName, null, "acceptor");
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, -1);

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmRaName, "java:/LocalJmsXA", PrepareConstants.INVM_CONNECTOR_NAME);
            jmsAdminOperations.setDefaultResourceAdapter(inVmRaName);
        }

        // remote InServer and local OutServer
        if (isServerRemote(inServer) && !isServerRemote(outServer)) {

            // now reconfigure hornetq-ra which is used for inbound to connect to remote server
            jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingName, jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, remoteSocketBindingName, null, "acceptor");
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, -1);
            jmsAdminOperations.setJndiNameForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, "java:jboss/DefaultJMSConnectionFactory");

            jmsAdminOperations.close();
            container.restart();
            jmsAdminOperations = container.getJmsOperations();

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmRaName, "java:/JmsXA", PrepareConstants.INVM_CONNECTOR_NAME);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(inVmRaName, -1);
        }

        jmsAdminOperations.close();
        container.stop();
    }

    private boolean isServerRemote(int container) {
        if (container == 1 || container == 3) {
            return true;
        } else {
            return false;
        }
    }
}
