package org.jboss.qa.hornetq.tools.journal;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.EapVersion;
import org.jboss.qa.hornetq.tools.JavaProcessBuilder;
import org.jboss.qa.hornetq.tools.ServerPathUtils;


/**
 * Utilities to work with HornetQ's journal export/import tool in EAP 6.
 */
public class JournalExportImportUtilsImplEAP6 implements JournalExportImportUtils {

    private static final Logger LOG = Logger.getLogger(JournalExportImportUtilsImplEAP6.class);

    private static final String HORNETQ_MODULE_PATH = "org/hornetq".replaceAll("/", File.separator);
    private static final String NETTY_MODULE_PATH = "org/jboss/netty".replaceAll("/", File.separator);
    private static final String LOGGING_MODULE_PATH = "org/jboss/logging".replaceAll("/", File.separator);

    private static final String EXPORT_TOOL_MAIN_CLASS = "org.hornetq.jms.persistence.impl.journal.XmlDataExporter";
    private static final String IMPORT_TOOL_MAIN_CLASS = "org.hornetq.jms.persistence.impl.journal.XmlDataImporter";

    private static final String EAP_60_EXPORT_TOOL_MAIN_CLASS = "org.hornetq.core.persistence.impl.journal.XmlDataExporter";
    private static final String EAP_60_IMPORT_TOOL_MAIN_CLASS = "org.hornetq.core.persistence.impl.journal.XmlDataImporter";


    /**
     * Export HornetQ journal from the given container to the given file.
     *
     * @param container Container where the journal is exported from.
     * @param exportedFileName Output file name with the exported journal.
     * @return True if the export succeeded.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean exportHornetQJournal(final ContainerInfo container, final String exportedFileName)
            throws IOException, InterruptedException {

        LOG.info("Exporting journal from container " + container.getName() + " to file " + exportedFileName);

        // TODO: move standalone/domain path to ContainerInfo
        String pathToJournal = getHornetQJournalDirectory(container.getJbossHome(), "standalone");
        File journalDirectory = new File(pathToJournal);
        if (!(journalDirectory.exists() && journalDirectory.isDirectory() && journalDirectory.canRead())) {
            LOG.error("Cannot read from journal directory " + pathToJournal);
            return false;
        }


        JavaProcessBuilder processBuilder = new JavaProcessBuilder();
        processBuilder.setWorkingDirectory(new File(".").getAbsolutePath());
        processBuilder.addClasspathEntry(journalToolClassPath(container.getJbossHome()));

        EapVersion eapVersion = EapVersion.fromEapVersionFile(container.getJbossHome());
        if (eapVersion.compareToString("6.0.1") <= 0) {
            processBuilder.setMainClass(EAP_60_EXPORT_TOOL_MAIN_CLASS);
        } else {
            processBuilder.setMainClass(EXPORT_TOOL_MAIN_CLASS);
        }

        processBuilder.addArgument(pathToJournal + File.separator + "messagingbindings");
        processBuilder.addArgument(pathToJournal + File.separator + "messagingjournal");
        processBuilder.addArgument(pathToJournal + File.separator + "messagingpaging");
        processBuilder.addArgument(pathToJournal + File.separator + "mesaaginglargemessages");

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
     * Import HornetQ journal from the given container to the given file.
     *
     * @param container Container where the journal is exported from.
     * @param exportedFileName Output file name with the exported journal.
     * @return True if the export succeeded.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean importHornetQJournal(final ContainerInfo container, final String exportedFileName)
            throws IOException, InterruptedException {

        LOG.info("Importing journal from file " + exportedFileName + " to container " + container.getName());

        JavaProcessBuilder processBuilder = new JavaProcessBuilder();
        processBuilder.setWorkingDirectory(new File(".").getAbsolutePath());
        //processBuilder.mergeErrorStreamWithOutput(false);
        processBuilder.addClasspathEntry(journalToolClassPath(container.getJbossHome()));

        EapVersion eapVersion = EapVersion.fromEapVersionFile(container.getJbossHome());
        if (eapVersion.compareToString("6.0.1") <= 0) {
            processBuilder.setMainClass(EAP_60_IMPORT_TOOL_MAIN_CLASS);
        } else {
            processBuilder.setMainClass(IMPORT_TOOL_MAIN_CLASS);
        }

        processBuilder.addArgument(new File(exportedFileName).getAbsolutePath());
        processBuilder.addArgument(container.getIpAddress());
        processBuilder.addArgument(String.valueOf(HornetQTestCaseConstants.PORT_HORNETQ_DEFAULT + container.getPortOffset()));
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


    private static String journalToolClassPath(final String jbossHome) throws IOException {
        String classpath = getModuleJarsClasspath(jbossHome, HORNETQ_MODULE_PATH) + File.pathSeparator
                + getModuleJarsClasspath(jbossHome, NETTY_MODULE_PATH) + File.pathSeparator
                + getModuleJarsClasspath(jbossHome, LOGGING_MODULE_PATH);
        LOG.info("Setting up classpath for the export tool: " + classpath);
        return classpath;
    }


    private static String getModuleJarsClasspath(final String jbossHome, final String modulePath) throws IOException {
        return ServerPathUtils.getModuleDirectory(jbossHome, modulePath).getAbsolutePath() + File.separator + "*";
    }


    private static String getHornetQJournalDirectory(final String jbossHome, final String profile) {
        return jbossHome + File.separator + profile + File.separator + "data";
    }

}
