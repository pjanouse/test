package org.jboss.qa.hornetq.test.soak;


import java.util.List;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public interface SoakTestModule {

    void setUpServers();


    List<ClassDeploymentDefinition> getRequiredClasses();


    List<FileDeploymentDefinition> getRequiredAssets();

}
