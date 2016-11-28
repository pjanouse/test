package org.jboss.qa.hornetq;

import java.util.Iterator;
import java.util.ServiceLoader;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.HornetQAdminOperationsEAP6;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ModelNodeUtils;
import org.jboss.qa.hornetq.tools.ProcessIdUtils;


public class DomainNodeEAP6 implements DomainNode {

    private static final Logger LOG = Logger.getLogger(DomainNodeEAP6.class);

    private final String name;
    private final String hostname;
    private final int port;
    private final ContainerController containerController;

    private JmxUtils jmxUtils = null;

    DomainNodeEAP6(String name, String hostname, int port, ContainerController containerController) {
        this.name = name;
        this.hostname = hostname;
        this.port = port;
        this.containerController = containerController;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHostname() {
        return hostname;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getJNDIPort() {
        return Constants.JNDI_PORT_DEFAULT_EAP6 + getPortOffset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getHornetqPort() {
        return Constants.PORT_HORNETQ_DEFAULT_EAP6 + getPortOffset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JMSOperations getRuntimeJmsOperations() {
        HornetQAdminOperationsEAP6 eap6AdmOps = new HornetQAdminOperationsEAP6();
        eap6AdmOps.setHostname(hostname);
        eap6AdmOps.setPort(port);
        eap6AdmOps.connect();

        eap6AdmOps.addAddressPrefix("host", "master");
        eap6AdmOps.addAddressPrefix("server", name);

        return eap6AdmOps;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("start");
        model.get(ClientConstants.OP_ADDR).add("host", "master");
        model.get(ClientConstants.OP_ADDR).add("server-config", name);

        LOG.info("Starting node " + name);
        try {
            ModelNodeUtils.applyOperation(hostname, port, model);
        } catch (Exception e) {
            throw new RuntimeException("Error while starting domain node " + name, e);
        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            LOG.warn("Waiting for node startup was interrupted");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("stop");
        model.get(ClientConstants.OP_ADDR).add("host", "master");
        model.get(ClientConstants.OP_ADDR).add("server-config", name);

        LOG.info("Stopping node " + name);
        try {
            ModelNodeUtils.applyOperation(hostname, port, model);
        } catch (Exception e) {
            throw new RuntimeException("Error while stopping domain node " + name, e);
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            LOG.warn("Waiting for node shutdown was interrupted");
        }
    }

    @Override
    public void kill() {
        LOG.info("Killing domain node " + name);
        try {
            long pid = ProcessIdUtils.getProcessId(this);

            if (System.getProperty("os.name").contains("Windows") || System.getProperty("os.name").contains("windows")) {
                // use taskkill
                Runtime.getRuntime().exec("taskkill /f /pid " + pid);
            } else { // on all other platforms use kill -9
                Runtime.getRuntime().exec("kill -9 " + pid);
            }
        } catch (Exception ex) {
            LOG.warn("Container " + getName() + " could not be killed. Set debug for logging to see exception stack trace.", ex);
            LOG.debug(ex);
        } finally {
            LOG.info("Server: " + getName() + " -- KILLED");
        }

//        if (containerController == null) {
//            System.out.println("ALERT - container controller is null in domain node object");
//        }
        //containerController.kill(name);
    }

    @Override
    public JmxUtils getJmxUtils() {
        if (jmxUtils == null) {
            ServiceLoader serviceLoader = ServiceLoader.load(JmxUtils.class);

            @SuppressWarnings("unchecked")
            Iterator<JmxUtils> iterator = serviceLoader.iterator();
            if (!iterator.hasNext()) {
                throw new RuntimeException("No implementation found for JmxUtils.");
            }
            jmxUtils = iterator.next();
        }

        return jmxUtils;
    }

    private int getPortOffset() {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("read-attribute");
        model.get(ClientConstants.OP_ADDR).add("host", "master");
        model.get(ClientConstants.OP_ADDR).add("server-config", name);
        model.get("name").set("socket-binding-port-offset");

        try {
            ModelNode result = ModelNodeUtils.applyOperation(hostname, port, model);
            return result.get("result").asInt();
        } catch (Exception e) {
            throw new RuntimeException("Error while reading port offset for domain node " + name, e);
        }
    }

}
