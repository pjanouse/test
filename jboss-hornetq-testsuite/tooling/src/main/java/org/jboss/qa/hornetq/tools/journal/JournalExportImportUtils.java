package org.jboss.qa.hornetq.tools.journal;

/**
 * @author mnovak@redhat.com
 */
public interface JournalExportImportUtils {
    boolean exportJournal(String exportedFileName)
            throws Exception;

    boolean importJournal(String exportedFileName)
            throws Exception;

    /**
     * Absolute path to directory which contains Journal directories (it's parent dir of messagingbindings, messagingjournal)
     *
     * @param path
     */
    void setPathToJournalDirectory(String path);
}
