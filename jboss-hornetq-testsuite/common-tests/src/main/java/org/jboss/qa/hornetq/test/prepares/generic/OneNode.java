package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareMethod;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class OneNode extends PrepareBase {

    @PrepareMethod(value = "OneNode", labels = {"EAP6"})
    public void prepareMethodEAP6(Map<String, Object> params) throws Exception {
        Container container = getContainer(params, 1);

        container.start();

        beforePrepareEAP6(params, container);
        prepareEAP6(params, container);
        afterPrepareEAP6(params, container);

        container.stop();
    }

    @PrepareMethod(value = "OneNode", labels = {"EAP7"})
    public void prepareMethodEAP7(Map<String, Object> params) throws Exception {
        Container container = getContainer(params, 1);

        container.start();

        beforePrepareEAP7(params, container);
        prepareEAP7(params, container);
        afterPrepareEAP7(params, container);

        container.stop();
    }

    protected void beforePrepare(Map<String, Object> params, Container container) throws Exception {

    }

    protected void beforePrepareEAP6(Map<String, Object> params, Container container) throws Exception {
        beforePrepare(params, container);
    }

    protected void beforePrepareEAP7(Map<String, Object> params, Container container) throws Exception {
        beforePrepare(params, container);
    }

    protected void prepare(Map<String, Object> params, Container container) throws Exception {
        JMSOperations jmsOperations = container.getJmsOperations();

        prepareDestinations(params, jmsOperations);
        prepareDiverts(params, jmsOperations);
        prepareAddressSettings(params, jmsOperations);
        prepareSecurity(params, container);
        prepareConnectionFactory(params, jmsOperations);
        prepareMisc(params, jmsOperations);

        jmsOperations.close();
    }

    protected void prepareEAP6(Map<String, Object> params, Container container) throws Exception {
        prepare(params, container);

        JMSOperations jmsOperations = container.getJmsOperations();
        prepareConnectorEAP6(params, jmsOperations);
        jmsOperations.close();
    }

    protected void prepareEAP7(Map<String, Object> params, Container container) throws Exception {
        prepare(params, container);

        JMSOperations jmsOperations = container.getJmsOperations();
        prepareConnectorEAP7(params, jmsOperations);
        jmsOperations.close();
    }

    protected void afterPrepare(Map<String, Object> params, Container container) throws Exception {

    }

    protected void afterPrepareEAP6(Map<String, Object> params, Container container) throws Exception {
        afterPrepare(params, container);
    }

    protected void afterPrepareEAP7(Map<String, Object> params, Container container) throws Exception {
        afterPrepare(params, container);
    }

}
