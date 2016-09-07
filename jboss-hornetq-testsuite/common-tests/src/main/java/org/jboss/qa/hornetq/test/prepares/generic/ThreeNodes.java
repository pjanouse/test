package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;
import java.util.Random;

public class ThreeNodes extends PrepareBase {

    @PrepareMethod(value = "ThreeNodes", labels = {"EAP6"})
    public void prepareMethodEAP6(final Map<String, Object> params) throws Exception {
        final Container container1 = getContainer(params, 1);
        final Container container2 = getContainer(params, 2);
        final Container container3 = getContainer(params, 3);

        container1.start();
        container2.start();
        container3.start();

        beforePrepareEAP6(params);

        final Map<String, Object> params1 = getParamsForContainer(params, 1);
        final Map<String, Object> params2 = getParamsForContainer(params, 2);
        final Map<String, Object> params3 = getParamsForContainer(params, 3);

        Runnable r1 = new Runnable() {
            @Override
            public void run() {
                try {
                    beforePrepareContainer1EAP6(params1, container1);
                    prepareContainer1EAP6(params1, container1);
                    afterPrepareContainer1EAP6(params1, container1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Runnable r2 = new Runnable() {
            @Override
            public void run() {
                try {
                    beforePrepareContainer2EAP6(params2, container2);
                    prepareContainer2EAP6(params2, container2);
                    afterPrepareContainer2EAP6(params2, container2);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Runnable r3 = new Runnable() {
            @Override
            public void run() {
                try {
                    beforePrepareContainer3EAP6(params3, container3);
                    prepareContainer3EAP6(params3, container3);
                    afterPrepareContainer3EAP6(params3, container3);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        PrepareUtils.runInParallel(r1, r2, r3);

        afterPrepareEAP6(params);

        container1.stop();
        container2.stop();
        container3.stop();
    }

    @PrepareMethod(value = "ThreeNodes", labels = {"EAP7"})
    public void prepareMethodEAP7(Map<String, Object> params) throws Exception {
        final Container container1 = getContainer(params, 1);
        final Container container2 = getContainer(params, 2);
        final Container container3 = getContainer(params, 3);

        container1.start();
        container2.start();
        container3.start();

        beforePrepareEAP7(params);

        final Map<String, Object> params1 = getParamsForContainer(params, 1);
        final Map<String, Object> params2 = getParamsForContainer(params, 2);
        final Map<String, Object> params3 = getParamsForContainer(params, 3);

        Runnable r1 = new Runnable() {
            @Override
            public void run() {
                try {
                    beforePrepareContainer1EAP7(params1, container1);
                    prepareContainer1EAP7(params1, container1);
                    afterPrepareContainer1EAP7(params1, container1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Runnable r2 = new Runnable() {
            @Override
            public void run() {
                try {
                    beforePrepareContainer2EAP7(params2, container2);
                    prepareContainer2EAP7(params2, container2);
                    afterPrepareContainer2EAP7(params2, container2);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Runnable r3 = new Runnable() {
            @Override
            public void run() {
                try {
                    beforePrepareContainer3EAP7(params3, container3);
                    prepareContainer3EAP7(params3, container3);
                    afterPrepareContainer3EAP7(params3, container3);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        PrepareUtils.runInParallel(r1, r2, r3);

        afterPrepareEAP7(params);

        container1.stop();
        container2.stop();
        container3.stop();
    }

    // Before

    protected void beforePrepare(Map<String, Object> params) throws Exception {

    }

    protected void beforePrepareEAP6(Map<String, Object> params) throws Exception {
        beforePrepare(params);
    }

    protected void beforePrepareEAP7(Map<String, Object> params) throws Exception {
        beforePrepare(params);
    }

    protected void beforePrepareContainer(Map<String, Object> params, Container container) throws Exception {

    }

    protected void beforePrepareContainerEAP6(Map<String, Object> params, Container container) throws Exception {
        beforePrepareContainer(params, container);
    }

    protected void beforePrepareContainerEAP7(Map<String, Object> params, Container container) throws Exception {
        beforePrepareContainer(params, container);
    }

    // Before container 1

    protected void beforePrepareContainer1EAP6(Map<String, Object> params, Container container) throws Exception {
        beforePrepareContainerEAP6(params, container);
    }

    protected void beforePrepareContainer1EAP7(Map<String, Object> params, Container container) throws Exception {
        beforePrepareContainerEAP7(params, container);
    }

    // Before container 2

    protected void beforePrepareContainer2EAP6(Map<String, Object> params, Container container) throws Exception {
        beforePrepareContainerEAP6(params, container);
    }

    protected void beforePrepareContainer2EAP7(Map<String, Object> params, Container container) throws Exception {
        beforePrepareContainerEAP7(params, container);
    }

    // Before container 3

    protected void beforePrepareContainer3EAP6(Map<String, Object> params, Container container) throws Exception {
        beforePrepareContainerEAP6(params, container);
    }

    protected void beforePrepareContainer3EAP7(Map<String, Object> params, Container container) throws Exception {
        beforePrepareContainerEAP7(params, container);
    }

    protected void prepareContainerEAP6(Map<String, Object> params, Container container) throws Exception {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setClustered(true);

        jmsOperations.setNodeIdentifier(new Random().nextInt());

        prepareSecurity(params, container);

        prepareAddressSettings(params, jmsOperations);

        prepareDestinations(params, jmsOperations);

        prepareDiverts(params, jmsOperations);

        prepareConnectionFactory(params, jmsOperations);

        prepareConnectorEAP6(params, jmsOperations);

        prepareCluster(params, jmsOperations);

        prepareMisc(params, jmsOperations);

        jmsOperations.close();
    }

    protected void prepareContainerEAP7(Map<String, Object> params, Container container) throws Exception {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setNodeIdentifier(new Random().nextInt());

        prepareSecurity(params, container);

        prepareAddressSettings(params, jmsOperations);

        prepareDestinations(params, jmsOperations);

        prepareDiverts(params, jmsOperations);

        prepareConnectionFactory(params, jmsOperations);

        prepareConnectorEAP7(params, jmsOperations);

        prepareCluster(params, jmsOperations);

        prepareMisc(params, jmsOperations);

        jmsOperations.close();
    }

    protected void prepareContainer1EAP6(Map<String, Object> params, Container container) throws Exception {
        prepareContainerEAP6(params, container);
    }

    protected void prepareContainer1EAP7(Map<String, Object> params, Container container) throws Exception {
        prepareContainerEAP7(params, container);
    }

    // Prepare container 2

    protected void prepareContainer2EAP6(Map<String, Object> params, Container container) throws Exception {
        prepareContainerEAP6(params, container);
    }

    protected void prepareContainer2EAP7(Map<String, Object> params, Container container) throws Exception {
        prepareContainerEAP7(params, container);
    }

    // Prepare container 3

    protected void prepareContainer3EAP6(Map<String, Object> params, Container container) throws Exception {
        prepareContainerEAP6(params, container);
    }

    protected void prepareContainer3EAP7(Map<String, Object> params, Container container) throws Exception {
        prepareContainerEAP7(params, container);
    }

    protected void afterPrepareContainer(Map<String, Object> params, Container container) throws Exception {

    }

    protected void afterPrepareContainerEAP6(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainer(params, container);
    }
    protected void afterPrepareContainerEAP7(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainer(params, container);
    }

    // After container 1

    protected void afterPrepareContainer1EAP6(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainerEAP6(params, container);
    }

    protected void afterPrepareContainer1EAP7(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainerEAP7(params, container);
    }

    // After container 2

    protected void afterPrepareContainer2EAP6(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainerEAP6(params, container);
    }

    protected void afterPrepareContainer2EAP7(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainerEAP7(params, container);
    }

    // After container 3

    protected void afterPrepareContainer3EAP6(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainerEAP6(params, container);
    }

    protected void afterPrepareContainer3EAP7(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainerEAP7(params, container);
    }

    // After prepare

    protected void afterPrepare(Map<String, Object> params) throws Exception {

    }

    protected void afterPrepareEAP6(Map<String, Object> params) throws Exception {
        afterPrepare(params);
    }

    protected void afterPrepareEAP7(Map<String, Object> params) throws Exception {
        afterPrepare(params);
    }

}
