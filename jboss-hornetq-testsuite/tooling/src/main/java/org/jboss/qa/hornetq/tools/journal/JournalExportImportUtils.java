package org.jboss.qa.hornetq.tools.journal;

import org.jboss.qa.hornetq.tools.ContainerInfo;

import java.io.IOException;

/**
 * @author mnovak@redhat.com
 */
public interface JournalExportImportUtils {
    boolean exportHornetQJournal(ContainerInfo container, String exportedFileName)
            throws Exception;

    boolean importHornetQJournal(ContainerInfo container, String exportedFileName)
                    throws Exception;
}
