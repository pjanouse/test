package org.jboss.qa.hornetq.junit.rules;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Map;

/**
 * Created by mnovak on 9/9/14.
 */
public class ArchiveServerLogsAfterFailedTest extends TestWatcher {

    private static final Logger log = Logger.getLogger(ArchiveServerLogsAfterFailedTest.class);

    @Override
    protected void failed(Throwable e, Description description) {

        log.info("Test: " + description.getClassName() + "." + description.getMethodName() + " failed. Archiving server logs for investigation.");

        try {
            archiveEAP6SeverLogs(description);
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
    public void archiveEAP6SeverLogs(Description description) throws Exception {

        ArquillianDescriptor descriptor = HornetQTestCase.getArquillianDescriptor();

        Map<String, String> containerProperties;
        String jbossHome;
        File serverLogDirectory;
        File whereToCopyServerLogDirectory;
        StringBuilder pathToServerLogDirectory;
        String fileSeparator = System.getProperty("file.separator");
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                containerProperties = containerDef.getContainerProperties();
                jbossHome = containerProperties.get("jbossHome");

                if (jbossHome != null) {
                    pathToServerLogDirectory = new StringBuilder(jbossHome)
                            .append(fileSeparator)
                            .append("standalone")
                            .append(fileSeparator)
                            .append("log");
                    serverLogDirectory = new File(pathToServerLogDirectory.toString());
                    if (!serverLogDirectory.exists()) {
                            log.info(String.format("Server log directory: %s does not exist. "
                                    + "This directory won't be archived."
                                    , serverLogDirectory.getAbsolutePath()));

                        continue;
                    }
                    whereToCopyServerLogDirectory = new File("target", description.getClassName() + "." + description.getMethodName()
                            + fileSeparator + containerDef.getContainerName() + "-log");

                    if (!whereToCopyServerLogDirectory.exists()) {
                        whereToCopyServerLogDirectory.mkdirs();
                    }

                    log.info("Copying log directory " + serverLogDirectory.getAbsolutePath()
                            + " to " + whereToCopyServerLogDirectory.getAbsolutePath());

                    copyDirectory(serverLogDirectory, whereToCopyServerLogDirectory);
                }
            }
        }
    }

    /**
     * Copies file from one place to another.
     *
     * @param sourceFile source file
     * @param destFile   destination file - file will be rewritten
     * @throws java.io.IOException
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
            for (String aChildren : children) {
                copyDirectory(new File(srcDir, aChildren),
                        new File(dstDir, aChildren));
            }
        } else {
            // This method is implemented in Copying a File
            copyFile(srcDir, dstDir);
        }
    }

}