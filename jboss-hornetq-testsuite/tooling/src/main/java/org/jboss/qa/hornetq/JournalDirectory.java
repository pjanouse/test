package org.jboss.qa.hornetq;


import java.io.File;

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

        boolean isDeleted = FileUtils.deleteQuietly(new File(getJournalDirectory(jbossHome, pathToJournal)));
        logger.info("Delete of journal directory: " + new File(getJournalDirectory(jbossHome, pathToJournal)).getAbsolutePath()
                + " - success=" + isDeleted);
    }

    private static String getJournalDirectory(final String jbossHome, final String pathToJournal) {
        if (new File(pathToJournal).isAbsolute()) {
            return pathToJournal;
        } else {
            // take relative path against data directory
            return jbossHome + File.separator + "standalone" + File.separator + "data"
                    + File.separator + pathToJournal.replaceAll("/", File.separator);
        }
    }

}
