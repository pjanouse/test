package org.jboss.qa.hornetq.test.soak.modules;


import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.soak.ClassDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.FileDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.SoakTestModule;
import org.jboss.qa.hornetq.test.soak.components.RemoteJcaResendingBean;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;

import java.util.ArrayList;
import java.util.List;


/**
 * Module testing remote JCA connections.
 * <p>
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

    private Container queuesContainer = container(1);

    private Container mdbContainer = container(2);

    private final boolean doRollbacks;


    public RemoteJcaSoakModule() {
        this(true);
    }


    public RemoteJcaSoakModule(final boolean doRollbacks) {
        this.doRollbacks = doRollbacks;
    }


    @Override
    public void setUpServers() {
        String resourceAdapterName = ContainerUtils.isEAP6(mdbContainer) ? Constants.RESOURCE_ADAPTER_NAME_EAP6 :
                Constants.RESOURCE_ADAPTER_NAME_EAP7;

        JMSOperations ops = mdbContainer.getJmsOperations();
        ops.addRemoteSocketBinding("messaging-remote", queuesContainer.getHostname(), queuesContainer.getHornetqPort());
        if (ContainerUtils.isEAP6(mdbContainer)) {
            ops.createRemoteConnector("remote-connector", "messaging-remote", null);
        } else {
            ops.createHttpConnector("remote-connector", "messaging-remote", null);
        }

        ops.setConnectorOnPooledConnectionFactory(resourceAdapterName, "remote-connector");
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
        String provider = ContainerUtils.isEAP6(mdbContainer) ? Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6 : Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7;

        // ip in property file on n-th mdb container points to n-th queue container
        Asset contents = new StringAsset("remote-jms-server=" + queuesContainer.getHostname() + "\n" +
                "remote-jms-jndi-port=" + queuesContainer.getJNDIPort() + "\n" +
                "provider=" + provider + "\n");
        assets.add(new FileDeploymentDefinition(contents, "remote-jca-resending-bean.properties",
                this.mdbContainer.getName()));
        return assets;
    }


    private void prepareQueues(final Container container) {
        JMSOperations ops = container.getJmsOperations();
        ops.createQueue(JCA_IN_QUEUE, JCA_IN_QUEUE_JNDI);
        ops.createQueue(JCA_OUT_QUEUE, JCA_OUT_QUEUE_JNDI);
        ops.close();
    }

}
