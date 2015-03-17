package org.jboss.qa.hornetq.test.soak;


import java.util.List;
import org.jboss.arquillian.container.test.api.ContainerController;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public interface SoakTestModule {

    void setUpServers(final ContainerController controller);


    List<ClassDeploymentDefinition> getRequiredClasses();


    List<FileDeploymentDefinition> getRequiredAssets();

}
