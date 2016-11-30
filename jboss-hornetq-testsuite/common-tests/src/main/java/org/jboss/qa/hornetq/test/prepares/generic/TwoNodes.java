package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JournalDirectory;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.test.prepares.PrepareMethods;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;
import java.util.Random;

public class TwoNodes extends PrepareBase {

    @PrepareMethod(value = "TwoNodes", labels = {"EAP6", "EAP7"})
    public void prepareMethod(final Map<String, Object> params, final PrepareContext ctx) throws Exception {
        final Container container1 = getContainer(params, 1);
        final Container container2 = getContainer(params, 2);

        container1.start();
        container2.start();

        JMSOperations jmsOperations1 = container1.getJmsOperations();
        JMSOperations jmsOperations2 = container2.getJmsOperations();

        beforePrepare(params, ctx);

        final Map<String, Object> params1 = getParamsForContainer(params, container1, jmsOperations1, 1);
        final Map<String, Object> params2 = getParamsForContainer(params, container2, jmsOperations2, 2);

        Runnable r1 = new Runnable() {
            @Override
            public void run() {
                try {
                    beforePrepareContainer1(params1, ctx);
                    prepareContainer1(params1, ctx);
                    afterPrepareContainer1(params1, ctx);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Runnable r2 = new Runnable() {
            @Override
            public void run() {
                try {
                    beforePrepareContainer2(params2, ctx);
                    prepareContainer2(params2, ctx);
                    afterPrepareContainer2(params2, ctx);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        PrepareUtils.runInParallel(r1, r2);

        afterPrepare(params, ctx);

        getJMSOperations(params1).close();
        getJMSOperations(params2).close();

        container1.stop();
        container2.stop();

        JournalDirectory.deleteJournalDirectory(null, HornetQTestCase.JOURNAL_DIRECTORY_A);
        JournalDirectory.deleteJournalDirectory(null, HornetQTestCase.JOURNAL_DIRECTORY_B);
        JournalDirectory.deleteJournalDirectory(null, HornetQTestCase.JOURNAL_DIRECTORY_C);
        JournalDirectory.deleteJournalDirectory(null, HornetQTestCase.JOURNAL_DIRECTORY_D);
    }

    // Before

    protected void beforePrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        PrepareUtils.setIfNotSpecified(params, "1." + PrepareParams.JOURNAL_BINDINGS_TABLE, "node1-bindings-table");
        PrepareUtils.setIfNotSpecified(params, "1." + PrepareParams.JOURNAL_MESSAGES_TABLE, "node1-messages-table");
        PrepareUtils.setIfNotSpecified(params, "1." + PrepareParams.JOURNAL_LARGE_MESSAGES_TABLE, "node1-large-messages-table");
        PrepareUtils.setIfNotSpecified(params, "2." + PrepareParams.JOURNAL_BINDINGS_TABLE, "node2-bindings-table");
        PrepareUtils.setIfNotSpecified(params, "2." + PrepareParams.JOURNAL_MESSAGES_TABLE, "node2-messages-table");
        PrepareUtils.setIfNotSpecified(params, "2." + PrepareParams.JOURNAL_LARGE_MESSAGES_TABLE, "node2-large-messages-table");
    }

    protected void beforePrepareContainer(Map<String, Object> params, PrepareContext ctx) throws Exception {

    }

    // Before container 1

    protected void beforePrepareContainer1(Map<String, Object> params, PrepareContext ctx) throws Exception {
        beforePrepareContainer(params, ctx);
    }

    // Before container 2

    protected void beforePrepareContainer2(Map<String, Object> params, PrepareContext ctx) throws Exception {
        beforePrepareContainer(params, ctx);
    }

    protected void prepareContainer(Map<String, Object> params, PrepareContext ctx) throws Exception {

        invokeAllPrepareMethods(params, ctx);
    }

    protected void prepareContainer1(Map<String, Object> params, PrepareContext ctx) throws Exception {
        prepareContainer(params, ctx);
    }

    // Prepare container 2

    protected void prepareContainer2(Map<String, Object> params, PrepareContext ctx) throws Exception {
        prepareContainer(params, ctx);
    }

    protected void afterPrepareContainer(Map<String, Object> params, PrepareContext ctx) throws Exception {

    }

    // After container 1

    protected void afterPrepareContainer1(Map<String, Object> params, PrepareContext ctx) throws Exception {
        afterPrepareContainer(params, ctx);
    }

    // After container 2

    protected void afterPrepareContainer2(Map<String, Object> params, PrepareContext ctx) throws Exception {
        afterPrepareContainer(params, ctx);
    }

    // After prepare

    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {

    }

}
