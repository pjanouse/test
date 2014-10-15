package org.jboss.qa.hornetq.tools.arquillian.extension;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.Before;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpAfterTest;
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
     * Deletes log, tmp, data after all tests annotated by @CleanUp.
     *
     * @param event      when to delete
     * @param descriptor arquillian.xml
     * @throws IOException
     */
    public void cleanUpAfterTest(@Observes After event, ArquillianDescriptor descriptor) throws IOException {

        // if there is no CleanUpAfterTest annotation then do nothing
        if (event.getTestMethod().getAnnotation(CleanUpAfterTest.class) == null)
            return;

        cleanUp(descriptor);
    }

    /**
     * Deletes log, tmp, data after all tests annotated by @CleanUp.
     *
     * @param event      when to delete
     * @param descriptor arquillian.xml
     * @throws IOException
     */
    public void cleanUpBeforeTest(@Observes Before event, ArquillianDescriptor descriptor) throws IOException {

        // if there is no CleanUpAfterTest annotation then do nothing
        if (event.getTestMethod().getAnnotation(CleanUpBeforeTest.class) == null)
            return;

        cleanUp(descriptor);
        cleanUpEAP5(descriptor);
    }

    private void cleanUp(ArquillianDescriptor descriptor)  {
        Map<String, String> containerProperties;
        String jbossHome;

        StringBuilder pathToStandaloneDirectory;
        String fileSeparator = System.getProperty("file.separator");


        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {

                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");
                pathToStandaloneDirectory = new StringBuilder(jbossHome)
                        .append(fileSeparator).append("standalone");

                if (! new File(pathToStandaloneDirectory.toString()).exists()) {
                    continue;
                }
                deleteFolder(new File(pathToStandaloneDirectory + fileSeparator + "tmp"));
                deleteFolder(new File(pathToStandaloneDirectory + fileSeparator + "log"));
                deleteFolder(new File(pathToStandaloneDirectory + fileSeparator + "data"));
                if (System.getProperty("JOURNAL_DIRECTORY_A") != null) {
                    deleteFolder(new File(System.getProperty("JOURNAL_DIRECTORY_A")));
                }
                if (System.getProperty("JOURNAL_DIRECTORY_B") != null) {
                    deleteFolder(new File(System.getProperty("JOURNAL_DIRECTORY_B")));
                }
                deleteFolder(new File(pathToStandaloneDirectory + fileSeparator + "data" + fileSeparator + (System.getProperty("JOURNAL_DIRECTORY_A") != null ? System.getProperty("JOURNAL_DIRECTORY_A") : "../../../../hornetq-journal-A")));
                deleteFolder(new File(pathToStandaloneDirectory + fileSeparator + "data" + fileSeparator + (System.getProperty("JOURNAL_DIRECTORY_B") != null ? System.getProperty("JOURNAL_DIRECTORY_B") : "../../../../hornetq-journal-B")));
            }
        }
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
                deleteFolder(new File(pathToConfigurationDirectory + fileSeparator + "tmp"));
                deleteFolder(new File(pathToConfigurationDirectory + fileSeparator + "log"));
                deleteFolder(new File(pathToConfigurationDirectory + fileSeparator + "data"));
                deleteFolder(new File(pathToConfigurationDirectory + fileSeparator + "work"));

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
            for (File file : files) {
                if (file.isDirectory()) {
                    successful = successful && deleteFolder(file);
                } else {
                    successful = successful && file.delete();
                }
            }
        }
        return successful && (path.delete());
    }

}

