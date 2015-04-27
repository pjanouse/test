// TODO Modify it for EAP 7
package org.jboss.qa.hornetq.tools.journal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.tools.EapVersion;
import org.jboss.qa.hornetq.tools.JavaProcessBuilder;
import org.jboss.qa.hornetq.tools.ServerPathUtils;
import org.kohsuke.MetaInfServices;

/**
 * Utilities to work with ActiveMQ's journal export/import tool in EAP 7.
 */
@MetaInfServices
public class JournalExportImportUtilsImplEAP7 implements JournalExportImportUtils {

    private static final Logger LOG = Logger.getLogger(JournalExportImportUtilsImplEAP7.class);

    private static final String ACTIVEMQ_MODULE_PATH = "org/apache/activemq".replaceAll("/", File.separator);
    private static final String NETTY_MODULE_PATH = "io/netty".replaceAll("/", File.separator);
    private static final String LOGGING_MODULE_PATH = "org/jboss/logging".replaceAll("/", File.separator);

    private static final String EAP_70_EXPORT_TOOL_MAIN_CLASS = "org.apache.activemq.tools.XmlDataExporter";
    private static final String EAP_70_IMPORT_TOOL_MAIN_CLASS = "org.apache.activemq.tools.XmlDataImporter";

    private String pathToJournal = null;

    /**
     * Export ActiveMQ journal from the given container to the given file.
     *
     * @param container Container where the journal is exported from.
     * @param exportedFileName Output file name with the exported journal.
     * @return True if the export succeeded.
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean exportJournal(Container container, final String exportedFileName) throws IOException, InterruptedException {

        LOG.info("Exporting journal from container " + container.getName() + " to file " + exportedFileName);

        if (pathToJournal == null || pathToJournal.equals("")) {
            pathToJournal = getJournalDirectory(container.getServerHome(), "standalone");
        }
        File journalDirectory = new File(pathToJournal);
        if (!(journalDirectory.exists() && journalDirectory.isDirectory() && journalDirectory.canRead())) {
            LOG.error("Cannot read from journal directory " + pathToJournal);
            return false;
        }

        JavaProcessBuilder processBuilder = new JavaProcessBuilder();
        processBuilder.setWorkingDirectory(new File(".").getAbsolutePath());
        processBuilder.addClasspathEntry(journalToolClassPath(container));
        processBuilder.setMainClass(EAP_70_EXPORT_TOOL_MAIN_CLASS);
        processBuilder.addArgument(pathToJournal + File.separator + "bindings");
        processBuilder.addArgument(pathToJournal + File.separator + "journal");
        processBuilder.addArgument(pathToJournal + File.separator + "paging");
        processBuilder.addArgument(pathToJournal + File.separator + "large-messages");

        Process exportProcess = processBuilder.startProcess();

        File exportedFile = new File(exportedFileName);
        if (!exportedFile.exists()) {
            exportedFile.createNewFile();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(exportProcess.getInputStream()));
        BufferedWriter writer = new BufferedWriter(new FileWriter(exportedFileName));

        String line;
        try {
            while ((line = reader.readLine()) != null && !line.trim().equalsIgnoreCase("<?xml version=\"1.0\"?>")) {
                // ignore anything before actual start of the XML
                // logger output gets mixed with the XML, so there will be some log lines before actual journal export XML
                if (line.trim().equalsIgnoreCase("<?xml version=\"1.0\"?>")) {
                    writer.write(line);
                    writer.write("\n");
                }
                LOG.info(line);
            }

            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write("\n");
                LOG.info(line); // duplicate output on stdout to have potential exceptions in the log
            }
        } finally {
            reader.close();
            writer.close();
        }

        int retval = exportProcess.waitFor();
        LOG.info("Journal export is done (with return code " + retval + ")");
        return retval == 0;
    }

    /**
     * Import ActiveMQ journal from the given container to the given file.
     *
     * @param container Container where the journal is exported from.
     * @param exportedFileName Output file name with the exported journal.
     * @return True if the export succeeded.
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean importJournal(Container container, final String exportedFileName) throws IOException, InterruptedException {

        LOG.info("Importing journal from file " + exportedFileName + " to container " + container.getName());

        JavaProcessBuilder processBuilder = new JavaProcessBuilder();
        processBuilder.setWorkingDirectory(new File(".").getAbsolutePath());
        // processBuilder.mergeErrorStreamWithOutput(false);
        processBuilder.addClasspathEntry(journalToolClassPath(container));

        EapVersion eapVersion = EapVersion.fromEapVersionFile(container.getServerHome());
        processBuilder.setMainClass(EAP_70_IMPORT_TOOL_MAIN_CLASS);

        processBuilder.addArgument(new File(exportedFileName).getAbsolutePath());
        processBuilder.addArgument(container.getHostname());
        processBuilder.addArgument(String.valueOf(container.getHornetqPort() + container.getPortOffset()));
        processBuilder.addArgument(String.valueOf(false));
        processBuilder.addArgument(String.valueOf(true));

        Process importProcess = processBuilder.startProcess();

        BufferedReader reader = new BufferedReader(new InputStreamReader(importProcess.getInputStream()));

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                LOG.info(line);
            }
        } finally {
            reader.close();
        }

        int retval = importProcess.waitFor();
        LOG.info("Journal import is done (with return code " + retval + ")");
        return retval == 0;
    }

    @Override
    public void setPathToJournalDirectory(String path) {
        this.pathToJournal = path;
    }

    private static String journalToolClassPath(Container container) throws IOException {
        String classpath = getModuleJarsClasspath(container, ACTIVEMQ_MODULE_PATH) + File.pathSeparator
                + getModuleJarsClasspath(container, NETTY_MODULE_PATH) + File.pathSeparator
                + getModuleJarsClasspath(container, LOGGING_MODULE_PATH);
        LOG.info("Setting up classpath for the export tool: " + classpath);
        return classpath;
    }

    private static String getModuleJarsClasspath(Container container, final String modulePath) throws IOException {
        return ServerPathUtils.getModuleDirectory(container, modulePath).getAbsolutePath() + File.separator + "*";
    }

    private static String getJournalDirectory(final String jbossHome, final String profile) {
        return jbossHome + File.separator + profile + File.separator + "data" + File.separator + "activemq";
    }

}
