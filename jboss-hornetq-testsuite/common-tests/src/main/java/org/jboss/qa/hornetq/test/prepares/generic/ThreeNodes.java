package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class ThreeNodes extends PrepareBase {

    @PrepareMethod(value = "ThreeNodes", labels = {"EAP6", "EAP7"})
    public void prepareMethod(final Map<String, Object> params, final PrepareContext ctx) throws Exception {
        final Container container1 = getContainer(params, 1);
        final Container container2 = getContainer(params, 2);
        final Container container3 = getContainer(params, 3);

        container1.start();
        container2.start();
        container3.start();

        JMSOperations jmsOperations1 = container1.getJmsOperations();
        JMSOperations jmsOperations2 = container2.getJmsOperations();
        JMSOperations jmsOperations3 = container3.getJmsOperations();

        beforePrepare(params, ctx);

        final Map<String, Object> params1 = getParamsForContainer(params, container1, jmsOperations1, 1);
        final Map<String, Object> params2 = getParamsForContainer(params, container2, jmsOperations2, 2);
        final Map<String, Object> params3 = getParamsForContainer(params, container3, jmsOperations3, 3);

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
        Runnable r3 = new Runnable() {
            @Override
            public void run() {
                try {
                    beforePrepareContainer3(params3, ctx);
                    prepareContainer3(params3, ctx);
                    afterPrepareContainer3(params3, ctx);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        PrepareUtils.runInParallel(r1, r2, r3);

        afterPrepare(params, ctx);

        getJMSOperations(params1).close();
        getJMSOperations(params2).close();
        getJMSOperations(params3).close();

        container1.stop();
        container2.stop();
        container3.stop();
    }

    // Before

    protected void beforePrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        PrepareUtils.setIfNotSpecified(params, "1." + PrepareParams.JOURNAL_BINDINGS_TABLE, "node1-bindings-table");
        PrepareUtils.setIfNotSpecified(params, "1." + PrepareParams.JOURNAL_MESSAGES_TABLE, "node1-messages-table");
        PrepareUtils.setIfNotSpecified(params, "1." + PrepareParams.JOURNAL_LARGE_MESSAGES_TABLE, "node1-large-messages-table");
        PrepareUtils.setIfNotSpecified(params, "2." + PrepareParams.JOURNAL_BINDINGS_TABLE, "node2-bindings-table");
        PrepareUtils.setIfNotSpecified(params, "2." + PrepareParams.JOURNAL_MESSAGES_TABLE, "node2-messages-table");
        PrepareUtils.setIfNotSpecified(params, "2." + PrepareParams.JOURNAL_LARGE_MESSAGES_TABLE, "node2-large-messages-table");
        PrepareUtils.setIfNotSpecified(params, "3." + PrepareParams.JOURNAL_BINDINGS_TABLE, "node3-bindings-table");
        PrepareUtils.setIfNotSpecified(params, "3." + PrepareParams.JOURNAL_MESSAGES_TABLE, "node3-messages-table");
        PrepareUtils.setIfNotSpecified(params, "3." + PrepareParams.JOURNAL_LARGE_MESSAGES_TABLE, "node3-large-messages-table");
    }

    protected void beforePrepareContainer(Map<String, Object> params, PrepareContext ctx) throws Exception {

    }

    protected void beforePrepareContainer1(Map<String, Object> params, PrepareContext ctx) throws Exception {
        beforePrepareContainer(params, ctx);
    }

    protected void beforePrepareContainer2(Map<String, Object> params, PrepareContext ctx) throws Exception {
        beforePrepareContainer(params, ctx);
    }

    protected void beforePrepareContainer3(Map<String, Object> params, PrepareContext ctx) throws Exception {
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

    protected void prepareContainer3(Map<String, Object> params, PrepareContext ctx) throws Exception {
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

    protected void afterPrepareContainer3(Map<String, Object> params, PrepareContext ctx) throws Exception {
        afterPrepareContainer(params, ctx);
    }

    // After prepare

    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {

    }

}
