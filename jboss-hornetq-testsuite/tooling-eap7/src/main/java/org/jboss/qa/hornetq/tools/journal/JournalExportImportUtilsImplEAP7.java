package org.jboss.qa.hornetq.tools.journal;

import org.apache.activemq.artemis.cli.commands.tools.DecodeJournal;
import org.apache.activemq.artemis.cli.commands.tools.EncodeJournal;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.tools.ServerPathUtils;
import org.kohsuke.MetaInfServices;

import java.io.*;
import java.util.regex.Matcher;

/**
 * Utilities to work with ActiveMQ's journal export/import tool in EAP 7.
 */
@MetaInfServices
public class JournalExportImportUtilsImplEAP7 implements JournalExportImportUtils {

    private static final Logger LOG = Logger.getLogger(JournalExportImportUtilsImplEAP7.class);

    private static final String ACTIVEMQ_MODULE_PATH = "org/apache/activemq/artemis".replaceAll("/", Matcher.quoteReplacement(File.separator));
    private static final String NETTY_MODULE_PATH = "io/netty".replaceAll("/", Matcher.quoteReplacement(File.separator));
    private static final String LOGGING_MODULE_PATH = "org/jboss/logging".replaceAll("/", Matcher.quoteReplacement(File.separator));

    private String pathToJournal = null;
    private Container container;

    public JournalExportImportUtilsImplEAP7(Container container) {
        this.container = container;
    }

    /**
     * Export ActiveMQ journal from the given container to the given file.
     *
     * @param exportedFileName Output file name with the exported journal.
     * @return True if the export succeeded.
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean exportJournal(final String exportedFileName) throws Exception {

        LOG.info("Exporting journal from container " + container.getName() + " to file " + exportedFileName);

        if (pathToJournal == null || pathToJournal.equals("")) {
            pathToJournal = getJournalDirectory(container.getServerHome(), "standalone");
        }
        File journalDirectory = new File(pathToJournal);
        if (!(journalDirectory.exists() && journalDirectory.isDirectory() && journalDirectory.canRead())) {
            LOG.error("Cannot read from journal directory " + pathToJournal);
            return false;
        }

        File exportedFileBindings = new File(exportedFileName + "bindings");
        if (!exportedFileBindings.exists()) {
            exportedFileBindings.createNewFile();
        }
        File exportedFileJournal = new File(exportedFileName + "journal");
        if (!exportedFileJournal.exists()) {
            exportedFileJournal.createNewFile();
        }

        PrintStream bindingsStream = new PrintStream(exportedFileBindings);
        PrintStream journalStream = new PrintStream(exportedFileJournal);

        EncodeJournal.exportJournal(pathToJournal + File.separator + "bindings", "activemq-bindings", "bindings", 2, 102400, bindingsStream);
        EncodeJournal.exportJournal(pathToJournal + File.separator + "journal", "activemq-data", "amq", 2, 102400, journalStream);

        boolean err1 = bindingsStream.checkError();
        bindingsStream.close();

        boolean err2 = bindingsStream.checkError();
        journalStream.close();

        return !err1 && !err2;
    }

    /**
     * Import ActiveMQ journal from the given container to the given file.
     *
     * @param exportedFileName Output file name with the exported journal.
     * @return True if the export succeeded.
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean importJournal(final String exportedFileName) throws IOException, InterruptedException, Exception {

        if (pathToJournal == null || pathToJournal.equals("")) {
            pathToJournal = getJournalDirectory(container.getServerHome(), "standalone");
        }
        File bindingsDirectory = new File(pathToJournal + File.separator + "bindings");
        File journalDirectory = new File(pathToJournal + File.separator + "journal");
        if (!bindingsDirectory.exists()) {
            bindingsDirectory.mkdirs();
        }
        if (!journalDirectory.exists()) {
            journalDirectory.mkdirs();
        }

        File exportedFileBindings = new File(exportedFileName + "bindings");
        if (!exportedFileBindings.exists()) {
            throw new RuntimeException("file exported bindings doesnt exists");
        }
        File exportedFileJournal = new File(exportedFileName + "journal");
        if (!exportedFileJournal.exists()) {
            throw new RuntimeException("file exported journal doesnt exists");
        }

        FileInputStream fis = new FileInputStream(exportedFileBindings);
        String bindigsString = readStream(fis);
        fis.close();

        fis = new FileInputStream(exportedFileJournal);
        String journalString = readStream(fis);
        fis.close();

        LOG.info("Importing bindings from file " + exportedFileName + " to container " + container.getName());
        DecodeJournal.importJournal(bindingsDirectory.getAbsolutePath(), "activemq-bindings", "bindings", 2, 102400, new StringReader(bindigsString));

        LOG.info("Importing journal from file " + exportedFileName + " to container " + container.getName());
        DecodeJournal.importJournal(journalDirectory.getAbsolutePath(), "activemq-data", "amq", 2, 102400, new StringReader(journalString));

        return true;
    }

    @Override
    public void setPathToJournalDirectory(String path) {
        this.pathToJournal = path;
    }

    private  String journalToolClassPath() throws IOException {
        String classpath = getModuleJarsClasspath(ACTIVEMQ_MODULE_PATH) + File.pathSeparator
                + getModuleJarsClasspath(NETTY_MODULE_PATH) + File.pathSeparator
                + getModuleJarsClasspath(LOGGING_MODULE_PATH);
        LOG.info("Setting up classpath for the export tool: " + classpath);
        return classpath;
    }

    private  String getModuleJarsClasspath(final String modulePath) throws IOException {
        return ServerPathUtils.getModuleDirectory(container, modulePath).getAbsolutePath() + File.separator + "*";
    }

    private static String getJournalDirectory(final String jbossHome, final String profile) {
        return jbossHome + File.separator + profile + File.separator + "data" + File.separator + "activemq";
    }

    private String readStream(InputStream is) {
        StringBuilder sb = new StringBuilder(512);
        try {
            Reader r = new InputStreamReader(is, "UTF-8");
            int c = 0;
            while ((c = r.read()) != -1) {
                sb.append((char) c);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }
}
