package org.jboss.qa.hornetq.tools.journal;

import org.jboss.qa.hornetq.Container;

/**
 * @author mnovak@redhat.com
 */
public interface JournalExportImportUtils {
    boolean exportHornetQJournal(Container container, String exportedFileName)
            throws Exception;

    boolean importHornetQJournal(Container container, String exportedFileName)
                    throws Exception;
}
