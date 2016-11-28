package org.jboss.qa.hornetq;


import org.apache.commons.io.FileUtils;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;


public class JournalDirectory {

    private static final Logger logger = Logger.getLogger(JournalDirectory.class);

    private JournalDirectory() {
    }

    public static void deleteJournalDirectory(final String jbossHome, final String pathToJournal) {
        logger.info("Trying to delete journal directory with path to journal: " + pathToJournal);
        if (pathToJournal == null || "".equals(pathToJournal)) {
            return;
        }

        try {
            FileUtils.forceDelete(new File(getJournalDirectory(jbossHome, pathToJournal)));
            logger.info("Delete of journal directory: " + new File(getJournalDirectory(jbossHome, pathToJournal)).getAbsolutePath()
                    + " - success");
        } catch (IOException e) {
            logger.warn("Could not delete journal directory: " + new File(getJournalDirectory(jbossHome, pathToJournal)).getAbsolutePath()
                    + " - failed" + e.getMessage());
            logger.debug("Stacktrace of exception during delete of " + new File(getJournalDirectory(jbossHome, pathToJournal)).getAbsolutePath() + " : ", e);
        }
    }

    public static String getJournalDirectory(final String jbossHome, final String pathToJournal) {
        if (new File(pathToJournal).isAbsolute()) {
            return pathToJournal;
        } else {
            // take relative path against data directory
            return jbossHome + File.separator + "standalone" + File.separator + "data"
                    + File.separator + pathToJournal.replace("/", File.separator);

        }
    }
}