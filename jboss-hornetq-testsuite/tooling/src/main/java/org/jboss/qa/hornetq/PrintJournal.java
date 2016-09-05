package org.jboss.qa.hornetq;

import java.io.File;

/**
 * Created by mnovak on 3/31/15.
 */
public interface PrintJournal {

    public File printJournal(String relativePathToOutputFile) throws Exception;

    public File printJournal(String messagingbindingsDirectory, String messagingjournalDirectory, String messagingpagingDirectory, String outputFile) throws Exception;
}
