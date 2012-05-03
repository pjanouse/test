package org.jboss.qa.tools.arquillian.extension;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.AfterClass;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;

/**
 * Saves configuration before test class and then restores after each test which is annotated by @RestoreConfigAfterTest
 *
 * @author mnovak@redhat.com
 */
public class RestoreConfig {
    
    private static final Logger logger = Logger.getLogger(RestoreConfig.class);
    
    /**
     * Backups configuration of all containers.
     * 
     * @param event when to backup configuration
     * @param descriptor arquillian.xml
     * @throws IOException 
     */
    public void backupConfiguration(@Observes BeforeClass event, ArquillianDescriptor descriptor) throws IOException {

        Map<String, String> containerProperties = null;
        String jbossHome = null;
        String serverConfig = null;
        File configurationFile = null;
        File configurationFileBackup = null;
        StringBuilder pathToConfigurationDirectory = null;
        String fileSeparator = System.getProperty("file.separator");


        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                
                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");
                serverConfig = containerProperties.get("serverConfig");

                pathToConfigurationDirectory = new StringBuilder(jbossHome)
                    .append(fileSeparator).append("standalone").append(fileSeparator).append("configuration").append(fileSeparator);

                configurationFile = new File(pathToConfigurationDirectory.toString() + serverConfig);
                configurationFileBackup = new File(pathToConfigurationDirectory.toString() + serverConfig + ".backup");
                
                logger.info("Copying configuration file " + configurationFile.getAbsolutePath() 
                        + " to " + configurationFileBackup.getAbsolutePath());
                
                copyFile(configurationFile, configurationFileBackup);
            }
        }
    }
        
    /**
     * Restore configuration of all containers after test method annotated by @RestoreConfigAfterTest.
     * 
     * @param event when to backup configuration
     * @param descriptor arquillian.xml
     * @throws IOException 
     */
    public void restoreConfiguration(@Observes After event, ArquillianDescriptor descriptor) throws IOException {
        
        // if there is no RestoreConfigAfterTest annotation then do nothing
        if (event.getTestMethod().getAnnotation(RestoreConfigAfterTest.class) == null) return;
        
        Map<String, String> containerProperties = null;
        String jbossHome = null;
        String serverConfig = null;
        File configurationFile = null;
        File configurationFileBackup = null;
        StringBuilder pathToConfigurationDirectory = null;
        String fileSeparator = System.getProperty("file.separator");


        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                
                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");
                serverConfig = containerProperties.get("serverConfig");
                
                // Restore configuration
                pathToConfigurationDirectory = new StringBuilder(jbossHome)
                    .append(fileSeparator).append("standalone").append(fileSeparator).append("configuration").append(fileSeparator);

                configurationFile = new File(pathToConfigurationDirectory.toString() + serverConfig);
                configurationFileBackup = new File(pathToConfigurationDirectory.toString() + serverConfig + ".backup");
                
                logger.info("Restoring configuration file " + configurationFileBackup.getAbsolutePath()
                        + " to " + configurationFile.getAbsolutePath());
                
                copyFile(configurationFileBackup, configurationFile);
                
            }
        }
    }
    
    /**
     * Restore configuration of all containers after test class. Must be annotated by @RestoreConfigAfterTest.
     * 
     * @param event when to backup configuration
     * @param descriptor arquillian.xml
     * @throws IOException 
     */
    public void restoreConfiguration(@Observes AfterClass event, ArquillianDescriptor descriptor) throws IOException {
        
        // if there is no RestoreConfigAfterTest annotation then do nothing
        if (event.getTestClass().getAnnotation(RestoreConfigAfterTest.class) == null) return;
        
        Map<String, String> containerProperties = null;
        String jbossHome = null;
        String serverConfig = null;
        File configurationFile = null;
        File configurationFileBackup = null;
        StringBuilder pathToConfigurationDirectory = null;
        String fileSeparator = System.getProperty("file.separator");


        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                
                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");
                serverConfig = containerProperties.get("serverConfig");
                
                // Restore configuration
                pathToConfigurationDirectory = new StringBuilder(jbossHome)
                    .append(fileSeparator).append("standalone").append(fileSeparator).append("configuration").append(fileSeparator);

                configurationFile = new File(pathToConfigurationDirectory.toString() + serverConfig);
                configurationFileBackup = new File(pathToConfigurationDirectory.toString() + serverConfig + ".backup");
                
                logger.info("Restoring configuration file " + configurationFileBackup.getAbsolutePath()
                        + " to " + configurationFile.getAbsolutePath());
                
                copyFile(configurationFileBackup, configurationFile);
                
            }
        }
    }
    
    /**
     * Copies file from one place to another.
     * 
     * @param sourceFile source file
     * @param destFile destination file - file will be rewritten
     * @throws IOException 
     */
    public void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }
}

