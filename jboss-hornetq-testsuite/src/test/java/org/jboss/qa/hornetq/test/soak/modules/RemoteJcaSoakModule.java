package org.jboss.qa.hornetq.test.soak.modules;


import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.soak.ClassDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.FileDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.SoakTestModule;
import org.jboss.qa.hornetq.test.soak.components.RemoteJcaResendingBean;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;

import java.util.ArrayList;
import java.util.List;


/**
 * Module testing remote JCA connections.
 *
 * Container 1 contains two queues (in and out). Container 2 contains MDB. Messages from in queue are sent
 * to container 2 through JCA, then processed by MDB which sends them to out queue (again through JCA)
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class RemoteJcaSoakModule extends HornetQTestCase implements SoakTestModule {

    public final static String JCA_IN_QUEUE = "soak.jca.InQueue";

    public final static String JCA_IN_QUEUE_JNDI = "jms/queue/soak/jca/InQueue";

    public final static String JCA_OUT_QUEUE = "soak.jca.OutQueue";

    public final static String JCA_OUT_QUEUE_JNDI = "jms/queue/soak/jca/OutQueue";

    private final ContainerInfo queuesContainer;

    private final ContainerInfo mdbContainer;

    private final boolean doRollbacks;


    public RemoteJcaSoakModule() {
        this(CONTAINER1_INFO, CONTAINER2_INFO, true);
    }


    public RemoteJcaSoakModule(final ContainerInfo queuesContainer, final ContainerInfo mdbContainer) {
        this(queuesContainer, mdbContainer, true);
    }


    public RemoteJcaSoakModule(final ContainerInfo queuesContainer, final ContainerInfo mdbContainer,
            final boolean doRollbacks) {

        this.queuesContainer = queuesContainer;
        this.mdbContainer = mdbContainer;
        this.doRollbacks = doRollbacks;
    }


    @Override
    public void setUpServers(final ContainerController controller) {

        JMSOperations ops = this.getJMSOperations(this.mdbContainer.getName());
        ops.addRemoteSocketBinding("messaging-remote", this.queuesContainer.getIpAddress(), 5445 + queuesContainer.getPortOffset());
        ops.createRemoteConnector("netty-remote", "messaging-remote", null);
        ops.setConnectorOnPooledConnectionFactory("hornetq-ra", "netty-remote");
        ops.close();

        this.prepareQueues(this.queuesContainer);
        this.prepareQueues(this.mdbContainer);
    }


    @Override
    public List<ClassDeploymentDefinition> getRequiredClasses() {
        List<ClassDeploymentDefinition> deployments = new ArrayList<ClassDeploymentDefinition>(1);

        if (this.doRollbacks) {
            deployments.add(new ClassDeploymentDefinition(RemoteJcaResendingBean.class, this.mdbContainer.getName()));
        } else {
            deployments.add(new ClassDeploymentDefinition(RemoteJcaResendingBean.class, this.mdbContainer.getName()));
        }

        return deployments;
    }


    @Override
    public List<FileDeploymentDefinition> getRequiredAssets() {
        List<FileDeploymentDefinition> assets = new ArrayList<FileDeploymentDefinition>(1);

        // ip in property file on n-th mdb container points to n-th queue container
        Asset contents = new StringAsset("remote-jms-server=" + this.queuesContainer.getIpAddress() + "\n");
        Asset contents2 = new StringAsset("remote-jms-jndi-server=" + getJNDIPort(queuesContainer.getName()) + "\n");
        assets.add(new FileDeploymentDefinition(contents, "remote-jca-resending-bean.properties",
                this.mdbContainer.getName()));
        assets.add(new FileDeploymentDefinition(contents2, "remote-jca-resending-bean.properties",
                this.mdbContainer.getName()));
        return assets;
    }


    private void prepareQueues(final ContainerInfo container) {
        JMSOperations ops = this.getJMSOperations(container.getName());
        ops.createQueue(JCA_IN_QUEUE, JCA_IN_QUEUE_JNDI);
        ops.createQueue(JCA_OUT_QUEUE, JCA_OUT_QUEUE_JNDI);
        ops.close();
    }

}
