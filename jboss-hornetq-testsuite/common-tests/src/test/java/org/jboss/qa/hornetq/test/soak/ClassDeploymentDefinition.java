package org.jboss.qa.hornetq.test.soak;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class ClassDeploymentDefinition {

    private final Class<?> classToDeploy;

    private final String containerName;


    public ClassDeploymentDefinition(final Class<?> classToDeploy, final String containerName) {
        this.classToDeploy = classToDeploy;
        this.containerName = containerName;
    }


    public Class<?> getClassToDeploy() {
        return this.classToDeploy;
    }


    public String getContainerName() {
        return this.containerName;
    }

}
