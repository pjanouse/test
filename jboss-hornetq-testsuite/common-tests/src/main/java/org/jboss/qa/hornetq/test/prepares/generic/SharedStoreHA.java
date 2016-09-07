package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareMethod;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class SharedStoreHA extends TwoNodes {

    @Override
    @PrepareMethod(value = "SharedStoreHA", labels = {"EAP6"})
    public void prepareMethodEAP6(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP6(params);
    }

    @Override
    @PrepareMethod(value = "SharedStoreHA", labels = {"EAP7"})
    public void prepareMethodEAP7(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP7(params);
    }

    @Override
    protected void afterPrepareContainer1EAP6(Map<String, Object> params, Container container) throws Exception {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setFailoverOnShutdown(true);
        jmsOperations.setSharedStore(true);
        jmsOperations.setBindingsDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setJournalDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setPagingDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setLargeMessagesDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainer2EAP6(Map<String, Object> params, Container container) throws Exception {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setFailoverOnShutdown(true);
        jmsOperations.setSharedStore(true);
        jmsOperations.setBackup(true);
        jmsOperations.setBindingsDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setJournalDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setPagingDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setLargeMessagesDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainer1EAP7(Map<String, Object> params, Container container) throws Exception {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addHAPolicySharedStoreMaster(0, true);

        jmsOperations.setBindingsDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setJournalDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setPagingDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setLargeMessagesDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainer2EAP7(Map<String, Object> params, Container container) throws Exception {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addHAPolicySharedStoreSlave(true, 0, true, true, false, null, null, null, null);

        jmsOperations.setBindingsDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setJournalDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setPagingDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);
        jmsOperations.setLargeMessagesDirectory(HornetQTestCase.JOURNAL_DIRECTORY_A);

        jmsOperations.close();
    }


}
