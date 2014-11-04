package org.jboss.qa.hornetq.tools.arquillian.extension;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.Before;
import org.jboss.qa.hornetq.JournalDirectory;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Removed tmp, data, log directory after each test which is annotated by @CleanUpAfterTest or @CleanUpBeforeTest
 *
 * @author mnovak@redhat.com
 */
public class CleanUp {

    private static final Logger logger = Logger.getLogger(CleanUp.class);

    /**
     * Deletes log, tmp, data before all tests annotated by {@link CleanUpBeforeTest}.
     *
     * @param event      when to delete
     * @param descriptor arquillian.xml
     * @throws IOException
     */
    public void cleanUpBeforeTest(@Observes Before event, ArquillianDescriptor descriptor) throws IOException {

        // if there is no CleanUpBeforeTest annotation on either the test method or the test class then do nothing
        if (event.getTestMethod().getAnnotation(CleanUpBeforeTest.class) == null
                && event.getTestClass().getAnnotation(CleanUpBeforeTest.class) == null) {

            return;
        }

        cleanUp(descriptor);
        cleanUpDomain(descriptor);
        cleanUpEAP5(descriptor);
    }

    private void cleanUp(ArquillianDescriptor descriptor)  {
        Map<String, String> containerProperties;
        String jbossHome;

        StringBuilder pathToStandaloneDirectory;

        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {

                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");
                pathToStandaloneDirectory = new StringBuilder(jbossHome)
                        .append(File.separator).append("standalone");

                if (! new File(pathToStandaloneDirectory.toString()).exists()) {
                    continue;
                }

                FileUtils.deleteQuietly(new File(pathToStandaloneDirectory + File.separator + "tmp"));
                FileUtils.deleteQuietly(new File(pathToStandaloneDirectory + File.separator + "log"));
                FileUtils.deleteQuietly(new File(pathToStandaloneDirectory + File.separator + "data"));

                JournalDirectory.deleteJournalDirectoryA(jbossHome);
                JournalDirectory.deleteJournalDirectoryB(jbossHome);
            }
        }
    }

    private void cleanUpDomain(ArquillianDescriptor descriptor) {
        // note: unlike standalone server tests, we don't use container groups for domain
        for (ContainerDef containerDef : descriptor.getContainers()) {
            for (File nodeDirectory : getDomainNodeDirectories(containerDef)) {
                FileUtils.deleteQuietly(new File(nodeDirectory, "tmp"));
                FileUtils.deleteQuietly(new File(nodeDirectory, "log"));
                FileUtils.deleteQuietly(new File(nodeDirectory, "data"));
                FileUtils.deleteQuietly(new File(nodeDirectory, "work"));
            }

            JournalDirectory.deleteJournalDirectoryA(containerDef.getContainerProperties().get("jbossHome"));
            JournalDirectory.deleteJournalDirectoryB(containerDef.getContainerProperties().get("jbossHome"));
        }

//        JournalDirectory.deleteJournalDirectoryA(
//                descriptor.getContainers().get(0).getContainerProperties().get("jbossHome"));
//        JournalDirectory.deleteJournalDirectoryB(
//                descriptor.getContainers().get(0).getContainerProperties().get("jbossHome"));
    }

    private void cleanUpEAP5(ArquillianDescriptor descriptor)  {
        Map<String, String> containerProperties;
        String jbossHome;

        StringBuilder pathToConfigurationDirectory;
        String fileSeparator = System.getProperty("file.separator");

        String profileName = null;

        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {

                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");
                profileName = containerProperties.get("profileName");

                pathToConfigurationDirectory = new StringBuilder(jbossHome)
                        .append(fileSeparator).append("server").append(fileSeparator).append(profileName);

                if (! new File(pathToConfigurationDirectory.toString()).exists()) {
                    continue;
                }
                FileUtils.deleteQuietly(new File(pathToConfigurationDirectory + fileSeparator + "tmp"));
                FileUtils.deleteQuietly(new File(pathToConfigurationDirectory + fileSeparator + "log"));
                FileUtils.deleteQuietly(new File(pathToConfigurationDirectory + fileSeparator + "data"));
                FileUtils.deleteQuietly(new File(pathToConfigurationDirectory + fileSeparator + "work"));

            }
        }
    }

    private List<File> getDomainNodeDirectories(ContainerDef domainContainer) {
        String jbossHome = domainContainer.getContainerProperties().get("jbossHome");
        String pathToNodes = jbossHome + File.separator + "domain" + File.separator + "servers";
        File serversDir = new File(pathToNodes);

        List<File> nodeDirectories = new ArrayList<File>();
        if (serversDir.isDirectory()) {
            for (String nodeDirPath : serversDir.list()) {
                File nodeDir = new File(nodeDirPath);
                if (nodeDir.isDirectory()) {
                    nodeDirectories.add(nodeDir);
                }
            }
        }

        return nodeDirectories;
    }

}

