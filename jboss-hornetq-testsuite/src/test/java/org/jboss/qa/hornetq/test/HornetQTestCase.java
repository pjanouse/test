package org.jboss.qa.hornetq.test;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.test.api.ArquillianResource;

/**
 * Parent class for all HornetQ test cases.
 */
public class HornetQTestCase {

    // Logger
    private static final Logger logger = Logger.getLogger(HornetQTestCase.class);

    protected static final String CONTAINER1 = "clustering-udp-0-unmanaged";

    protected static final String CONTAINER2 = "clustering-udp-1-unmanaged";

    @ArquillianResource
    protected ContainerController controller;


    // TODO implement methods for getting client of required type, ack-mode etc.

}
