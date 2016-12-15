package org.jboss.qa.hornetq.tools.journal;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.tools.EapVersion;
import org.jboss.qa.hornetq.tools.JavaProcessBuilder;
import org.jboss.qa.hornetq.tools.ServerPathUtils;
import org.kohsuke.MetaInfServices;

import java.io.*;


/**
 * Utilities to work with HornetQ's journal export/import tool in EAP 6.
 */
@MetaInfServices
public class JournalExportImportUtilsImplEAP6 implements JournalExportImportUtils {

    private static final Logger LOG = Logger.getLogger(JournalExportImportUtilsImplEAP6.class);

    private static final String HORNETQ_MODULE_PATH = "org/hornetq".replac("/", File.separator);
    private static final String NETTY_MODULE_PATH = "org/jboss/netty".replace("/", File.separator);
    private static final String LOGGING_MODULE_PATH = "org/jboss/logging".replace("/", File.separator);

    private static final String EXPORT_TOOL_MAIN_CLASS = "org.hornetq.jms.persistence.impl.journal.XmlDataExporter";
    private static final String IMPORT_TOOL_MAIN_CLASS = "org.hornetq.jms.persistence.impl.journal.XmlDataImporter";

    private static final String EAP_60_EXPORT_TOOL_MAIN_CLASS = "org.hornetq.core.persistence.impl.journal.XmlDataExporter";
    private static final String EAP_60_IMPORT_TOOL_MAIN_CLASS = "org.hornetq.core.persistence.impl.journal.XmlDataImporter";


    private String pathToJournal = null;
    private Container container;

    public JournalExportImportUtilsImplEAP6(Container container){
        this.container = container;
    }

    /**
     * Export HornetQ journal from the given container to the given file.
     *
     * @param exportedFileName Output file name with the exported journal.
     * @return True if the export succeeded.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean exportJournal(final String exportedFileName)
            throws IOException, InterruptedException {

        if (pathToJournal == null || pathToJournal.equals("")) {
            pathToJournal = getHornetQJournalDirectory(container.getServerHome(), "standalone");
        }
        LOG.info("Exporting journal from: " + pathToJournal + " to file " + exportedFileName);
        File journalDirectory = new File(pathToJournal);
        if (!(journalDirectory.exists() && journalDirectory.isDirectory() && journalDirectory.canRead())) {
            LOG.error("Cannot read from journal directory " + pathToJournal);
            return false;
        }

        JavaProcessBuilder processBuilder = new JavaProcessBuilder();
        processBuilder.setWorkingDirectory(new File(".").getAbsolutePath());
        processBuilder.addClasspathEntry(journalToolClassPath());

        EapVersion eapVersion = EapVersion.fromEapVersionFile(container.getServerHome());
        if (eapVersion.compareToString("6.0.1") <= 0) {
            processBuilder.setMainClass(EAP_60_EXPORT_TOOL_MAIN_CLASS);
        } else {
            processBuilder.setMainClass(EXPORT_TOOL_MAIN_CLASS);
        }

        processBuilder.addArgument(pathToJournal + File.separator + "messagingbindings");
        processBuilder.addArgument(pathToJournal + File.separator + "messagingjournal");
        processBuilder.addArgument(pathToJournal + File.separator + "messagingpagings");
        processBuilder.addArgument(pathToJournal + File.separator + "messaginglargemessages");

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
     * @param exportedFileName Output file name with the exported journal.
     * @return True if the export succeeded.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean importJournal(final String exportedFileName)
            throws IOException, InterruptedException {

        LOG.info("Importing journal from file " + exportedFileName + " to container " + container.getName());

        JavaProcessBuilder processBuilder = new JavaProcessBuilder();
        processBuilder.setWorkingDirectory(new File(".").getAbsolutePath());
        //processBuilder.mergeErrorStreamWithOutput(false);
        processBuilder.addClasspathEntry(journalToolClassPath());

        EapVersion eapVersion = EapVersion.fromEapVersionFile(container.getServerHome());
        if (eapVersion.compareToString("6.0.1") <= 0) {
            processBuilder.setMainClass(EAP_60_IMPORT_TOOL_MAIN_CLASS);
        } else {
            processBuilder.setMainClass(IMPORT_TOOL_MAIN_CLASS);
        }

        processBuilder.addArgument(new File(exportedFileName).getAbsolutePath());
        processBuilder.addArgument(container.getHostname());
        processBuilder.addArgument(String.valueOf(container.getHornetqPort() + container.getPortOffset()));
        processBuilder.addArgument(String.valueOf(false));
        processBuilder.addArgument(String.valueOf(true));

        Process importProcess = processBuilder.startProcess();

        BufferedReader reader = new BufferedReader(new InputStreamReader(importProcess.getInputStream()));

        String line;
        boolean noExceptionsEncountered = true;
        try {
            while ((line = reader.readLine()) != null) {
                LOG.info(line);
                if (noExceptionsEncountered) {
                    noExceptionsEncountered = !line.contains("Exception");
                }
            }
        } finally {
            reader.close();
        }

        int retval = importProcess.waitFor();

        if (!noExceptionsEncountered) {
            LOG.error("There was an exception during the journal import process, see logs for details...");
        }

        LOG.info("Journal import is done (with return code " + retval + ")");
        return (retval == 0) && noExceptionsEncountered;
    }

    @Override
    public void setPathToJournalDirectory(String path) {
        this.pathToJournal = path;
    }


    private String journalToolClassPath() throws IOException {
        String classpath = getModuleJarsClasspath(HORNETQ_MODULE_PATH) + File.pathSeparator
                + getModuleJarsClasspath(NETTY_MODULE_PATH) + File.pathSeparator
                + getModuleJarsClasspath(LOGGING_MODULE_PATH);
        LOG.info("Setting up classpath for the export tool: " + classpath);
        return classpath;
    }


    private String getModuleJarsClasspath(final String modulePath) throws IOException {
        return ServerPathUtils.getModuleDirectory(container, modulePath).getAbsolutePath() + File.separator + "*";
    }


    private static String getHornetQJournalDirectory(final String jbossHome, final String profile) {
        return jbossHome + File.separator + profile + File.separator + "data";
    }

}
