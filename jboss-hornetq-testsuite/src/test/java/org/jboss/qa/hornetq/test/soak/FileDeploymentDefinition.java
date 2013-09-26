package org.jboss.qa.hornetq.test.soak;


import org.jboss.shrinkwrap.api.asset.Asset;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class FileDeploymentDefinition {

    private final Asset contents;

    private final String targetName;

    private final String containerName;


    public FileDeploymentDefinition(final Asset contents, final String targetName, final String containerName) {
        this.contents = contents;
        this.targetName = targetName;
        this.containerName = containerName;
    }


    public Asset getContents() {
        return this.contents;
    }


    public String getTargetName() {
        return this.targetName;
    }


    public String getContainerName() {
        return this.containerName;
    }

}
