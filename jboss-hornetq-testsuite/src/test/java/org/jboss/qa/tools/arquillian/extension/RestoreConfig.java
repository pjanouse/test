package org.jboss.qa.tools.arquillian.extension;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.AfterClass;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Map;

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
     * @param event      when to backup configuration
     * @param descriptor arquillian.xml
     * @throws IOException
     */
    public void backupConfiguration(@Observes BeforeClass event, ArquillianDescriptor descriptor) throws IOException {

        Map<String, String> containerProperties;
        String jbossHome;
        String serverConfig;
        File configurationFile;
        File configurationFileBackup;
        StringBuilder pathToConfigDir;
        String fileSeparator = System.getProperty("file.separator");
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");
                serverConfig = containerProperties.get("serverConfig");
                serverConfig = (serverConfig == null) ? containerProperties.get("profileName") : serverConfig;
                if (jbossHome != null && serverConfig != null) {
                    pathToConfigDir = new StringBuilder(jbossHome)
                            .append(fileSeparator)
                            .append("standalone")
                            .append(fileSeparator)
                            .append("configuration")
                            .append(fileSeparator);
                    configurationFile = new File(pathToConfigDir.toString() + serverConfig);
                    if (!configurationFile.exists()) {
                        if (logger.isDebugEnabled()) {
                            logger.debug(String.format("Configuration file: %s does not exist. "
                                    + "Probably container EAP5 is used. Trying to create backup of EAP 5 configuration."
                                    , configurationFile.getAbsolutePath()));
                        }
                        backupConfigurationEAP5(descriptor);
                        return;
                    }
                    configurationFileBackup = new File(pathToConfigDir.toString() + serverConfig + ".backup");

                    if (configurationFileBackup.exists()) {
                        return;
                    }

                    logger.info("Copying configuration file " + configurationFile.getAbsolutePath()
                            + " to " + configurationFileBackup.getAbsolutePath());

                    copyFile(configurationFile, configurationFileBackup);
                }
            }
        }
    }

    /**
     * Backup EAP 5 profile configuration.
     *
     * @param descriptor arquillian descriptor (arquillian.xml)
     */
    public void backupConfigurationEAP5(ArquillianDescriptor descriptor) throws IOException {

        Map<String, String> containerProperties;
        String jbossHome;
        String profileName;
        StringBuilder pathToConfigurationDirectory;
        StringBuilder pathToBackupConfigurationDirectory;
        File originalProfileDirectory;
        File backupProfileDirectory;
        String fileSeparator = System.getProperty("file.separator");
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");
                profileName = containerProperties.get("profileName");
                if (jbossHome != null && profileName != null) {
                    pathToConfigurationDirectory = new StringBuilder(jbossHome)
                            .append(fileSeparator)
                            .append("server")
                            .append(fileSeparator)
                            .append(profileName);
                    pathToBackupConfigurationDirectory = new StringBuilder(jbossHome)
                            .append(fileSeparator)
                            .append("server")
                            .append(fileSeparator)
                            .append(profileName)
                            .append("-backup");
                    originalProfileDirectory = new File(pathToConfigurationDirectory.toString());
                    backupProfileDirectory = new File(pathToBackupConfigurationDirectory.toString());

                    if (!originalProfileDirectory.exists()) {
                        logger.warn(String.format("Profile %s does NOT exist. "
                                + "Backup of the configuration cannot be created.", profileName));
                        return;
                    }
                    if (!backupProfileDirectory.exists()) {
                        backupProfileDirectory.mkdir();
                    } else {
                        return;
                    }

                    copyDirectory(originalProfileDirectory, backupProfileDirectory);
                }
            }
        }
    }

    /**
     * Restore configuration of all containers after test method annotated by @RestoreConfigAfterTest.
     *
     * @param event      when to backup configuration
     * @param descriptor arquillian.xml
     * @throws IOException
     */
    public void restoreConfiguration(@Observes After event, ArquillianDescriptor descriptor) throws IOException {

        // if there is no RestoreConfigAfterTest annotation then do nothing
        if (event.getTestMethod().getAnnotation(RestoreConfigAfterTest.class) == null) return;

        logger.info("Restoring configuration after test: " + event.getTestMethod().getName());

        restoreConfiguration(descriptor);
    }

    /**
     * Restore configuration of all containers after test class. Must be annotated by @RestoreConfigAfterTest.
     *
     * @param event      when to backup configuration
     * @param descriptor arquillian.xml
     * @throws IOException
     */
    public void restoreConfiguration(@Observes AfterClass event, ArquillianDescriptor descriptor) throws IOException {

        // if there is no RestoreConfigAfterTest annotation then do nothing
        if (event.getTestClass().getAnnotation(RestoreConfigAfterTest.class) == null) return;

        logger.info("Restoring configuration after test: " + event.getTestClass().getName());

        restoreConfiguration(descriptor);
    }

    private void restoreConfiguration(ArquillianDescriptor descriptor) throws IOException {

        Map<String, String> containerProperties;
        String jbossHome;
        String serverConfig;
        File configurationFile;
        File configurationFileBackup;
        StringBuilder pathToConfigurationDirectory;
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

                if (!configurationFile.exists()) {
                    logger.warn("Configuration file " + configurationFile.getAbsolutePath() + " does not exist. Probably EAP 5 is used. " +
                            "Trying to restore EAP 5 profile.");
                    restoreConfigurationEAP5(descriptor);
                    return;
                }

                configurationFileBackup = new File(pathToConfigurationDirectory.toString() + serverConfig + ".backup");

                if (!configurationFileBackup.exists()) {
                    logger.error("Backup configuration file " + configurationFileBackup.getAbsolutePath() + " was not created. Configuration won't be restored.");
                }

                logger.info("Restoring configuration file " + configurationFileBackup.getAbsolutePath()
                        + " to " + configurationFile.getAbsolutePath());

                copyFile(configurationFileBackup, configurationFile);
            }
        }
    }

    private void restoreConfigurationEAP5(ArquillianDescriptor descriptor) throws IOException {

        Map<String, String> containerProperties;
        String jbossHome;
        String profileName;
        StringBuilder pathToConfigurationDirectory;
        StringBuilder pathToBackupConfigurationDirectory;
        File originalProfileDirectory;
        File backupProfileDirectory;

        String fileSeparator = System.getProperty("file.separator");

        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {

                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");
                profileName = containerProperties.get("profileName");

                pathToConfigurationDirectory = new StringBuilder(jbossHome)
                        .append(fileSeparator).append("server").append(fileSeparator).append(profileName);
                pathToBackupConfigurationDirectory = new StringBuilder(jbossHome)
                        .append(fileSeparator).append("server").append(fileSeparator).append(profileName).append("-backup");
                originalProfileDirectory = new File(pathToConfigurationDirectory.toString());
                backupProfileDirectory = new File(pathToBackupConfigurationDirectory.toString());

                if (!originalProfileDirectory.exists()) {
                    logger.warn("Profile " + profileName + " does NOT exist. Path: " + originalProfileDirectory + " Profile cannot be restored.");
                    return;
                }
                if (!backupProfileDirectory.exists()) {
                    logger.error("Backup for profile " + profileName + " does not exist. Configuration won't be restored.");
                    return;
                }
                copyDirectory(backupProfileDirectory, originalProfileDirectory);
            }
        }
    }

    /**
     * Copies file from one place to another.
     *
     * @param sourceFile source file
     * @param destFile   destination file - file will be rewritten
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

    /**
     * Copies one directory to another.
     *
     * @param srcDir source directory
     * @param dstDir destination directory
     * @throws IOException
     */
    public void copyDirectory(File srcDir, File dstDir) throws IOException {
        if (srcDir.isDirectory()) {
            if (!dstDir.exists()) {
                dstDir.mkdir();
            }

            String[] children = srcDir.list();
            for (int i = 0; i < children.length; i++) {
                copyDirectory(new File(srcDir, children[i]),
                        new File(dstDir, children[i]));
            }
        } else {
            // This method is implemented in Copying a File
            copyFile(srcDir, dstDir);
        }
    }

}

