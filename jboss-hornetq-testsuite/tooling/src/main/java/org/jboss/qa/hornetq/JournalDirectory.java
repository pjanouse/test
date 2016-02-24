package org.jboss.qa.hornetq;


import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;


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
            FileUtils.deleteDirectory(new File(getJournalDirectory(jbossHome, pathToJournal)));
            logger.info("Delete of journal directory: " + new File(getJournalDirectory(jbossHome, pathToJournal)).getAbsolutePath()
                    + " - success");
        } catch (IOException e) {
            logger.warn("Could not delete journal directory: " + new File(getJournalDirectory(jbossHome, pathToJournal)).getAbsolutePath()
                    + " - failed", e);
        }
    }

    private static String getJournalDirectory(final String jbossHome, final String pathToJournal) {
        if (new File(pathToJournal).isAbsolute()) {
            return pathToJournal;
        } else {
            // take relative path against data directory

            return jbossHome + File.separator + "standalone" + File.separator + "data"
                    + File.separator + pathToJournal.replace("/", File.separator);
        }
    }

}
