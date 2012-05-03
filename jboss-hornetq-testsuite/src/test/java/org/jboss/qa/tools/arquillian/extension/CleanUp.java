package org.jboss.qa.tools.arquillian.extension;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Map;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.AfterClass;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;

/**
 * Saves configuration before test class and then restores after each test which is annotated by @CleanUp
 *
 * @author mnovak@redhat.com
 */
public class CleanUp {
    
    private static final Logger logger = Logger.getLogger(CleanUp.class);
        
    /**
     * Deletes log, tmp, data after all tests annotated by @CleanUp.
     * 
     * @param event when to delete
     * @param descriptor arquillian.xml
     * @throws IOException 
     */
    public void cleanUpAfterTest(@Observes After event, ArquillianDescriptor descriptor) throws IOException {
        
        // if there is no CleanUpAfterTest annotation then do nothing
        if (event.getTestMethod().getAnnotation(org.jboss.qa.tools.arquillina.extension.annotation.CleanUpAfterTest.class) == null) return;
        
        Map<String, String> containerProperties = null;
        String jbossHome = null;
        
        StringBuilder pathToStandaloneDirectory = null;
        String fileSeparator = System.getProperty("file.separator");


        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                
                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");
                pathToStandaloneDirectory = new StringBuilder(jbossHome)
                    .append(fileSeparator).append("standalone");
                
                deleteFolder(new File(pathToStandaloneDirectory + fileSeparator + "tmp"));
                deleteFolder(new File(pathToStandaloneDirectory + fileSeparator + "log"));
                deleteFolder(new File(pathToStandaloneDirectory + fileSeparator + "data"));
                
            }
        }
    }
    
    /**
     * Deletes given folder and all sub folders
     *
     * @param path folder which should be deleted
     * @return true if operation was successful, false otherwise
     */
    protected boolean deleteFolder(File path) {
        logger.info(String.format("Removing folder '%s'", path));
        boolean successful = true;
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    successful = successful && deleteFolder(files[i]);
                } else {
                    successful = successful && files[i].delete();
                }
            }
        }
        return successful && (path.delete());
    }
    
}

