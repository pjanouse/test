package org.jboss.qa.hornetq.tools.arquillian.extension;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.spi.event.container.BeforeStart;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.Before;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;
import org.jboss.qa.hornetq.tools.DomainOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Saves configuration before @TestClass - ALWAYS
 * Restores after each test which is annotated by @RestoreConfigAfterTest
 * Restores before test which is annotated by @RestoreConfigBeforeTest
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
    public void backupConfigurationObserver(@Observes BeforeClass event, ArquillianDescriptor descriptor) throws IOException {
        // we'll going to backup both standalone and domain configurations no matter the arquillian configuration
        backupStandaloneConfiguration(descriptor);
        backupDomainConfiguration(descriptor);
        backupConfigurationEAP5(descriptor);
    }

    /**
     * Backups EAP6 domain configuration.
     *
     * @param descriptor arquillian.xml
     * @throws IOException
     */
    private void backupDomainConfiguration(ArquillianDescriptor descriptor) throws IOException {
        for (ContainerDef containerDef : descriptor.getContainers()) {
            JbossConfigFiles files = JbossConfigFiles.forContainer(containerDef);

            if (!files.getDomainConfigDir().exists()) {
                // don't bother, this isn't EAP6
                continue;
            }

            File domainXml = files.getDomainXml();
            File domainXmlBackup = files.getDomainXmlBackup();
            if (domainXml.exists() && !domainXmlBackup.exists()) {
                logger.info("Copying configuration file " + domainXml.getAbsolutePath()
                        + " to " + domainXmlBackup.getAbsolutePath());
                FileUtils.copyFile(domainXml, domainXmlBackup);
            }

            File hostXml = files.getHostXml();
            File hostXmlBackup = files.getHostXmlBackup();
            if (hostXml.exists() && !hostXmlBackup.exists()) {
                logger.info("Copying configuration file " + hostXml.getAbsolutePath()
                        + " to " + hostXmlBackup.getAbsolutePath());
                FileUtils.copyFile(hostXml, hostXmlBackup);
            }
        }
    }

    /**
     * Backups standalone configuration of all containers.
     *
     * Works for both EAP6 and EAP5
     *
     * @param descriptor arquillian.xml
     * @throws IOException
     */
    private void backupStandaloneConfiguration(ArquillianDescriptor descriptor) throws IOException {
        logger.info("Trying to back up standalone config");
        for (GroupDef groupDef : descriptor.getGroups()) {
            logger.info("Backing up configs for group " + groupDef.getGroupName());
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                logger.info("Backing up standalone config for container " + containerDef.getContainerName());
                JbossConfigFiles files = JbossConfigFiles.forContainer(containerDef);

                if (!files.getStandaloneConfigDir().exists()) {
                    logger.info("not EAP6");
                    // don't bother, this isn't EAP6
                    continue;
                }

                File standaloneXml = files.getStandaloneXml();
                File standaloneXmlBackup = files.getStandaloneXmlBackup();
                if (standaloneXml.exists() && !standaloneXmlBackup.exists()) {
                    logger.info("Copying configuration file " + standaloneXml.getAbsolutePath()
                            + " to " + standaloneXmlBackup.getAbsolutePath());
                    FileUtils.copyFile(standaloneXml, standaloneXmlBackup);
                }
            }
        }
    }

    /**
     * Backup EAP 5 profile configuration.
     *
     * @param descriptor arquillian descriptor (arquillian.xml)
     */
    private void backupConfigurationEAP5(ArquillianDescriptor descriptor) throws IOException {

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

                    FileUtils.copyDirectory(originalProfileDirectory, backupProfileDirectory);
                }
            }
        }
    }

    /**
     * Restore configuration of all containers before test class. Must be annotated by {@link RestoreConfigBeforeTest}.
     *
     * @param event      when to backup configuration
     * @param descriptor arquillian.xml
     * @throws IOException
     */
    public void restoreConfigurationBeforeTest(@Observes Before event, ArquillianDescriptor descriptor) throws IOException {

        // if there is no RestoreConfigBeforeTest annotation then do nothing
        if (event.getTestMethod().getAnnotation(RestoreConfigBeforeTest.class) == null) {
            return;
        }

        restoreStandaloneConfiguration(descriptor);
        restoreDomainConfiguration(descriptor);
        restoreConfigurationEAP5(descriptor);
    }


    private void restoreDomainConfiguration(ArquillianDescriptor descriptor) throws IOException {
        for (ContainerDef containerDef : descriptor.getContainers()) {
            JbossConfigFiles files = JbossConfigFiles.forContainer(containerDef);

            if (!files.getDomainConfigDir().exists()) {
                // don't bother, this isn't EAP6 domain
                continue;
            }

            File domainXml = files.getDomainXml();
            File domainXmlBackup = files.getDomainXmlBackup();
            if (domainXmlBackup.exists()) {
                logger.info("Restoring configuration file " + domainXmlBackup.getAbsolutePath()
                        + " to " + domainXml.getAbsolutePath());
                FileUtils.copyFile(domainXmlBackup, domainXml);
            }

            File hostXml = files.getHostXml();
            File hostXmlBackup = files.getHostXmlBackup();
            if (hostXmlBackup.exists()) {
                logger.info("Restoring configuration file " + hostXmlBackup.getAbsolutePath()
                        + " to " + hostXml.getAbsolutePath());
                FileUtils.copyFile(hostXmlBackup, hostXml);
            }
        }

//        logger.info("Reloading container after configuration restore");
//        DomainOperations.forDefaultContainer().reloadDomain();
//        logger.info("ContainerReloaded");
    }


    private void restoreStandaloneConfiguration(ArquillianDescriptor descriptor) throws IOException {
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                JbossConfigFiles files = JbossConfigFiles.forContainer(containerDef);

                if (!files.getStandaloneConfigDir().exists()) {
                    // don't bother, this isn't EAP6
                    continue;
                }

                File standaloneXml = files.getStandaloneXml();
                File standaloneXmlBackup = files.getStandaloneXmlBackup();
                if (standaloneXmlBackup.exists()) {
                    logger.info("Copying configuration file " + standaloneXmlBackup.getAbsolutePath()
                            + " to " + standaloneXml.getAbsolutePath());
                    FileUtils.copyFile(standaloneXmlBackup, standaloneXml);
                }
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
                    continue;
                }
                if (!backupProfileDirectory.exists()) {
                    logger.error("Backup for profile " + profileName + " does not exist. Configuration won't be restored.");
                    continue;
                }
                // TODO NEPREPISUJ LOGY
                // TODO NEMAZ PROFILE PO TESTU ALE PRED TESTEM
                FileUtils.copyDirectory(backupProfileDirectory, originalProfileDirectory);
            }
        }
    }

}

