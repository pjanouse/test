package org.jboss.qa.hornetq;

/**
 * Created by mnovak on 3/31/15.
 */
public interface PrintJournal {

    public void printJournal(String relativePathToOutputFile) throws Exception;

    public void printJournal(String messagingbindingsDirectory, String messagingjournalDirectory, String messagingpagingDirectory, String outputFile) throws Exception;
}
