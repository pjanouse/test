package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.test.prepares.PrepareMethods;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class OneNode extends PrepareBase {

    @PrepareMethod(value = "OneNode", labels = {"EAP6", "EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        Container container = getContainer(params, 1);

        container.start();

        JMSOperations jmsOperations = container.getJmsOperations();
        Map<String, Object> params1 = getParamsForContainer(params, container, jmsOperations, 1);

        beforePrepare(params1, ctx);
        prepare(params1, ctx);
        afterPrepare(params1, ctx);

        getJMSOperations(params1).close();
        container.stop();
    }
    protected void beforePrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {

    }

    protected void prepare(Map<String, Object> params, PrepareContext ctx) throws Exception {

        invokeAllPrepareMethods(params, ctx);

    }

    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {

    }

}
