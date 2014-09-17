package org.jboss.qa.hornetq;


import java.io.File;
import org.apache.commons.io.FileUtils;


public class JournalDirectory {

    private JournalDirectory() {
    }

    public static String getJournalDirectoryA(final String jbossHome) {
        return getJournalDirectory(jbossHome, "A");
    }

    public static String getJournalDirectoryB(final String jbossHome) {
        return getJournalDirectory(jbossHome, "B");
    }

    public static void deleteJournalDirectoryA(final String jbossHome) {
        deleteJournalDirectory(jbossHome, "A");
    }

    public static void deleteJournalDirectoryB(final String jbossHome) {
        deleteJournalDirectory(jbossHome, "B");
    }

    private static void deleteJournalDirectory(final String jbossHome, final String journal) {
        FileUtils.deleteQuietly(new File(getJournalDirectory(jbossHome, journal)));
    }

    private static String getJournalDirectory(final String jbossHome, final String journal) {
        String journalProperty = System.getProperty("JOURNAL_DIRECTORY_" + journal);

        if (journalProperty == null) {
            return jbossHome + File.separator + ".." + File.separator + ".."
                    + File.separator + "journal-directory-" + journal;
        } else if (new File(journalProperty).isAbsolute()) {
            return journalProperty;
        } else {
            throw new IllegalArgumentException("Relative path in JOURNAL_DIRECTORY_" + journal + " is not supported");
        }
    }

}
