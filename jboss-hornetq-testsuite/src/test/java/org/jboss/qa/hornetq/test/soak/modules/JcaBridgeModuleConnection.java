package org.jboss.qa.hornetq.test.soak.modules;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.soak.components.JcaBridgeConnectionBean;
import org.jboss.qa.hornetq.test.soak.ClassDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.FileDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.SoakTestModule;
import org.jboss.qa.hornetq.tools.ContainerInfo;


/**
 * Module with MDB that re-sends messages from remote jca module to bridge module.
 *
 * Requires {@link RemoteJcaSoakModule} and {@link BridgeSoakModule} to be active as well.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class JcaBridgeModuleConnection extends HornetQTestCase implements SoakTestModule {

    private final ContainerInfo container;


    public JcaBridgeModuleConnection() {
        this(CONTAINER1_INFO);
    }


    public JcaBridgeModuleConnection(final ContainerInfo queueContainer) {
        this.container = queueContainer;
    }


    @Override
    public void setUpServers(final ContainerController controller) {
    }


    @Override
    public List<ClassDeploymentDefinition> getRequiredClasses() {
        return Arrays.asList(new ClassDeploymentDefinition(
                JcaBridgeConnectionBean.class, this.container.getName()));
    }


    @Override
    public List<FileDeploymentDefinition> getRequiredAssets() {
        return new ArrayList<FileDeploymentDefinition>();
    }

}
