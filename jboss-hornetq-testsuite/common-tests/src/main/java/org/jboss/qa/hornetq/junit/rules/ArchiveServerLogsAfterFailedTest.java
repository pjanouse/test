package org.jboss.qa.hornetq.junit.rules;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.ZipUtils;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Map;

/**
 * @author mnovak@redhat.com
 */
public class ArchiveServerLogsAfterFailedTest extends TestWatcher {

    private static final Logger log = Logger.getLogger(ArchiveServerLogsAfterFailedTest.class);

    @Override
    protected void failed(Throwable e, Description description) {

        log.info("Test: " + description.getClassName() + "." + description.getMethodName() + " failed. Archiving server logs for investigation.");

        try {
            archiveEAPSeverLogs(description);
        } catch (Exception e1) {
            log.error("Archiving server logs for test " + description.getClassName() + "." + description.getMethodName()
                    + " failed. Check exception and test log for more details.", e1);
        }

        log.info("Test: " + description.getClassName() + "." + description.getMethodName() + " failed. Archiving server logs for investigation - finished");

    }

    /**
     * Archive standalone log directory of all servers.
     *
     * @throws IOException
     */
    public void archiveEAPSeverLogs(Description description) throws Exception {

        ArquillianDescriptor descriptor = HornetQTestCase.getArquillianDescriptor();

        Map<String, String> containerProperties;
        String jbossHome;
        String configurationFileName;
        File serverLogDirectory;
        File serverDataDirectory;
        File whereToCopyServerLogDirectory;
        File configurationFile;
        StringBuilder pathToServerLogDirectory;
        StringBuilder pathToDataDirectory;
        StringBuilder pathToConfigurationFile;
        String fileSeparator = System.getProperty("file.separator");
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");
                configurationFileName = containerProperties.get("serverConfig");
                if (jbossHome != null) {
                    // if eap 6 then go to standalone/log/ directory
                    // else if eap 5 go to server/${profile}/log directory
                    pathToServerLogDirectory = new StringBuilder(jbossHome)
                            .append(fileSeparator)
                            .append("standalone")
                            .append(fileSeparator)
                            .append("log");
                    pathToDataDirectory = new StringBuilder(jbossHome)
                            .append(fileSeparator)
                            .append("standalone")
                            .append(fileSeparator)
                            .append("data");
                    pathToConfigurationFile = new StringBuilder(jbossHome)
                            .append(fileSeparator)
                            .append("standalone")
                            .append(fileSeparator)
                            .append("configuration")
                            .append(fileSeparator)
                            .append(configurationFileName);
                    serverLogDirectory = new File(pathToServerLogDirectory.toString());
                    serverDataDirectory = new File(pathToDataDirectory.toString());
                    configurationFile = new File(pathToConfigurationFile.toString());
                    if (!serverLogDirectory.exists()) {      // if does not exist try eap 5 directory
                        log.info(String.format("Server log directory: %s does not exist. ",
                                serverLogDirectory.getAbsolutePath()));
                        pathToServerLogDirectory = new StringBuilder(jbossHome)
                                .append(fileSeparator)
                                .append("server")
                                .append(fileSeparator)
                                .append(containerProperties.get("profileName"))
                                .append(fileSeparator)
                                .append("log");
                        pathToDataDirectory = new StringBuilder(jbossHome)
                                .append(fileSeparator)
                                .append("server")
                                .append(fileSeparator)
                                .append(containerProperties.get("profileName"))
                                .append(fileSeparator)
                                .append("data");
                        pathToConfigurationFile = new StringBuilder(jbossHome)
                                .append(fileSeparator)
                                .append("server")
                                .append(fileSeparator)
                                .append(containerProperties.get("profileName"))
                                .append(fileSeparator)
                                .append("deploy")
                                .append(fileSeparator)
                                .append("hornetq");
                        serverLogDirectory = new File(pathToServerLogDirectory.toString());
                        serverDataDirectory = new File(pathToDataDirectory.toString());
                        configurationFile = new File(pathToConfigurationFile.toString());
                        if (!serverLogDirectory.exists()) {
                            log.info(String.format("Server log directory: %s does not exist. ",
                                    serverLogDirectory.getAbsolutePath()));
                            return;
                        }
                    }
                    whereToCopyServerLogDirectory = new File("target", description.getClassName() + "." + description.getMethodName()
                            + fileSeparator + containerDef.getContainerName() + "-log");

                    if (!whereToCopyServerLogDirectory.exists()) {
                        whereToCopyServerLogDirectory.mkdirs();
                    }

                    log.info("Copying log directory " + serverLogDirectory.getAbsolutePath()
                            + " to " + whereToCopyServerLogDirectory.getAbsolutePath());
                    ZipUtils.zipDir(new File(whereToCopyServerLogDirectory, "logs.zip"), serverLogDirectory);
                    log.info("Copying data directory " + serverDataDirectory.getAbsolutePath()
                            + " to " + whereToCopyServerLogDirectory.getAbsolutePath());
                    FileUtils.copyDirectory(serverDataDirectory, whereToCopyServerLogDirectory);
                    log.info("Copying xml configuration " + configurationFile.getAbsolutePath()
                            + " to " + whereToCopyServerLogDirectory.getAbsolutePath());
                    FileUtils.copyFileToDirectory(configurationFile, whereToCopyServerLogDirectory);
                }
            }
        }
    }

}
