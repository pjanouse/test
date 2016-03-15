package org.jboss.qa.hornetq;


import java.io.File;
import java.io.FileNotFoundException;
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
            String path = jbossHome + File.separator + "standalone" + File.separator + "data"
                    + File.separator + pathToJournal.replace("/", File.separator);

            if (!path.contains(".."))
                return path;
            else {
                String[] pathParts = path.split(File.separator);
                for (int i = 0; i < pathParts.length; i++) {
                    if (pathParts[i].equals("..")) {
                        pathParts[i] = null;
                        for (int j = i - 1; j > 0; j--) {
                            if (pathParts[j] != null && !pathParts[j].equals("..") && !pathParts[j].isEmpty()) {
                                pathParts[j] = null;
                                break;
                            }
                        }
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < pathParts.length; i++) {
                    if (pathParts[i] != null && !pathParts[i].isEmpty()) {
                        if (!System.getProperty("os.name").contains("win") || i != 0){
                            sb.append(File.separator);
                        }
                        sb.append(pathParts[i]);
                    }
                }
                return sb.toString();
            }

        }
    }


}
