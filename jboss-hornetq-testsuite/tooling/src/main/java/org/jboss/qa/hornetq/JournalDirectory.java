package org.jboss.qa.hornetq;


import java.io.File;

import org.apache.commons.io.FileUtils;


public class JournalDirectory {

    private JournalDirectory() {
    }

    public static void deleteJournalDirectory(final String jbossHome, final String pathToJournal) {
        if (pathToJournal != null || "".equals(pathToJournal)) {
            return;
        }
        FileUtils.deleteQuietly(new File(getJournalDirectory(jbossHome, pathToJournal)));
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
